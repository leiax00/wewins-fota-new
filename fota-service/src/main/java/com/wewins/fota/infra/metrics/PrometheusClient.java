package com.wewins.fota.infra.metrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.infra.config.properties.MonitoringProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prometheus HTTP API 客户端
 * <p>
 * 用于查询宿主机指标（CPU、内存、网络等）
 * </p>
 */
@Slf4j
@Component
public class PrometheusClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String prometheusUrl;
    private final boolean enabled;

    public PrometheusClient(MonitoringProperties monitoringProperties, ObjectMapper objectMapper) {
        MonitoringProperties.Prometheus prometheus = monitoringProperties.getPrometheus();
        this.prometheusUrl = prometheus.getUrl();
        this.enabled = prometheus.isEnabled();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 执行即时查询
     *
     * @param query PromQL 查询语句
     * @return 查询结果值，失败返回 empty
     */
    public Optional<Double> query(String query) {
        return queryVector(query).stream()
                .findFirst()
                .map(MetricSample::value);
    }

    /**
     * 执行向量查询。
     */
    public List<MetricSample> queryVector(String query) {
        if (!enabled) {
            return List.of();
        }

        try {
            String url = String.format("%s/api/v1/query?query=%s", prometheusUrl, 
                    java.net.URLEncoder.encode(query, "UTF-8"));
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                PrometheusResponse promResponse = objectMapper.readValue(
                        response.body(), PrometheusResponse.class);
                
                if ("success".equals(promResponse.status()) && 
                    promResponse.data() != null && 
                    promResponse.data().result() != null) {
                    List<MetricSample> samples = new ArrayList<>();
                    for (Map<String, Object> result : promResponse.data().result()) {
                        Object metric = result.get("metric");
                        Object value = result.get("value");
                        if (!(metric instanceof Map<?, ?> metricMap) || !(value instanceof List<?> list) || list.size() < 2) {
                            continue;
                        }
                        String valueStr = list.get(1).toString();
                        samples.add(new MetricSample(
                                castMetricMap(metricMap),
                                Double.parseDouble(valueStr),
                                Instant.ofEpochSecond(Long.parseLong(list.get(0).toString().split("\\.")[0]))
                        ));
                    }
                    samples.sort(Comparator.comparingDouble(MetricSample::value).reversed());
                    return samples;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to query Prometheus: {} - {}", query, e.getMessage());
        }
        return List.of();
    }

    /**
     * 查询范围数据
     */
    public Optional<List<MetricDataPoint>> queryRange(String query, long start, long end, String step) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            String url = String.format("%s/api/v1/query_range?query=%s&start=%d&end=%d&step=%s",
                    prometheusUrl,
                    java.net.URLEncoder.encode(query, "UTF-8"),
                    start, end, step);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 解析范围数据...
                log.debug("Range query successful: {}", query);
            }
        } catch (Exception e) {
            log.debug("Failed to query range from Prometheus: {} - {}", query, e.getMessage());
        }
        return Optional.empty();
    }

    public List<MetricSample> getTopProductsByCheckQps(String region, int limit) {
        String query = String.format(
                "topk(%d, sum(rate(fota_device_checks_total{region=\"%s\"}[5m])) by (product))",
                limit, region);
        return queryVector(query);
    }

    public List<MetricSample> getTopProductsByReportQps(String region, int limit) {
        String query = String.format(
                "topk(%d, sum(rate(fota_upgrade_events_total{region=\"%s\"}[5m])) by (product))",
                limit, region);
        return queryVector(query);
    }

    public double getRegionCheckQps(String region) {
        String query = String.format(
                "sum(rate(fota_device_checks_total{region=\"%s\"}[5m]))",
                region);
        return query(query).orElse(-1.0);
    }

    public double getRegionReportQps(String region) {
        String query = String.format(
                "sum(rate(fota_upgrade_events_total{region=\"%s\"}[5m]))",
                region);
        return query(query).orElse(-1.0);
    }

    public double getRegionHttpQps(String region) {
        String query = String.format(
                "sum(rate(http_server_requests_seconds_count{region=\"%s\"}[5m]))",
                region);
        return query(query).orElse(-1.0);
    }

    public double getRegionP99Latency(String region) {
        String query = String.format(
                "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region=\"%s\"}[5m])) by (le)) * 1000",
                region);
        return query(query).orElse(-1.0);
    }

    public double getBlockRate(String region, String resource) {
        String query = String.format(
                "sum(rate(fota_rate_limited_total{region=\"%s\",resource=\"%s\"}[5m])) / clamp_min(sum(rate(fota_device_checks_total{region=\"%s\"}[5m])), 1)",
                region, resource, region);
        return query(query).orElse(0.0);
    }

    public List<MetricSample> getHostsCpuUsage() {
        return queryVector("100 - (avg by (host) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)");
    }

    public List<MetricSample> getHostsMemoryUsage() {
        return queryVector("100 * (1 - avg by (host) (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes))");
    }

    public List<MetricSample> getHostsNetworkInBytes() {
        return queryVector("sum by (host) (irate(node_network_receive_bytes_total{device!=\"lo\"}[5m]))");
    }

    public List<MetricSample> getHostsNetworkOutBytes() {
        return queryVector("sum by (host) (irate(node_network_transmit_bytes_total{device!=\"lo\"}[5m]))");
    }

    public List<MetricSample> getInstancesCheckQps(String region, int limit) {
        String query = String.format(
                "topk(%d, sum(rate(fota_device_checks_total{region=\"%s\"}[5m])) by (instance))",
                limit, region);
        return queryVector(query);
    }

    public List<MetricSample> getInstancesReportQps(String region, int limit) {
        String query = String.format(
                "topk(%d, sum(rate(fota_upgrade_events_total{region=\"%s\"}[5m])) by (instance))",
                limit, region);
        return queryVector(query);
    }

    public List<MetricSample> getInstancesCpuUsage(String region) {
        String query = String.format(
                "avg by (instance) (system_cpu_usage{region=\"%s\"}) * 100",
                region);
        return queryVector(query);
    }

    public List<MetricSample> getInstancesHeapUsage(String region) {
        String query = String.format(
                "100 * sum by (instance) (jvm_memory_used_bytes{area=\"heap\",region=\"%s\"}) / clamp_min(sum by (instance) (jvm_memory_max_bytes{area=\"heap\",region=\"%s\"}), 1)",
                region, region);
        return queryVector(query);
    }

    /**
     * 获取宿主机 CPU 使用率
     *
     * @param hostLabel 主机标签
     * @return CPU 使用率 (0-100)
     */
    public double getHostCpuUsage(String hostLabel) {
        String query = String.format(
                "100 - (avg by (host) (irate(node_cpu_seconds_total{mode=\"idle\",host=\"%s\"}[5m])) * 100)",
                hostLabel);
        return query(query).orElse(-1.0);
    }

    /**
     * 获取宿主机内存使用率
     *
     * @param hostLabel 主机标签
     * @return 内存使用率 (0-100)
     */
    public double getHostMemoryUsage(String hostLabel) {
        String query = String.format(
                "100 * (1 - avg(node_memory_MemAvailable_bytes{host=\"%s\"} / node_memory_MemTotal_bytes{host=\"%s\"}))",
                hostLabel, hostLabel);
        return query(query).orElse(-1.0);
    }

    /**
     * 获取宿主机网络入站流量 (bytes/s)
     *
     * @param hostLabel 主机标签
     * @return 网络入站流量 bytes/s
     */
    public double getHostNetworkInBytes(String hostLabel) {
        String query = String.format(
                "sum(irate(node_network_receive_bytes_total{host=\"%s\",device!=\"lo\"}[5m]))",
                hostLabel);
        return query(query).orElse(-1.0);
    }

    /**
     * 获取宿主机网络出站流量 (bytes/s)
     *
     * @param hostLabel 主机标签
     * @return 网络出站流量 bytes/s
     */
    public double getHostNetworkOutBytes(String hostLabel) {
        String query = String.format(
                "sum(irate(node_network_transmit_bytes_total{host=\"%s\",device!=\"lo\"}[5m]))",
                hostLabel);
        return query(query).orElse(-1.0);
    }

    public double getApiQps(String uriPattern) {
        String query = String.format(
                "sum(rate(http_server_requests_seconds_count{uri=~\"%s\"}[1m]))",
                uriPattern);
        return query(query).orElse(-1.0);
    }

    private Map<String, String> castMetricMap(Map<?, ?> metricMap) {
        return metricMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                ));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PrometheusResponse(
            String status,
            @JsonProperty("data") PrometheusData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PrometheusData(
            String resultType,
            @JsonProperty("result") List<Map<String, Object>> result
    ) {}

    public record MetricDataPoint(long timestamp, double value) {}

    public record MetricSample(Map<String, String> metric, double value, Instant timestamp) {}
}
