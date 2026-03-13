package com.wewins.fota.application.monitor;

import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.adapter.api.admin.dto.*;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.infra.metrics.NodeIdentity;
import com.wewins.fota.infra.metrics.PrometheusClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MonitorOverviewService {

    private static final String RESOURCE_NAME = "upgrade:check";
    private static final Duration REALTIME_CACHE_TTL = Duration.ofSeconds(5);
    private static final Duration TRENDS_CACHE_TTL = Duration.ofSeconds(15);
    private final SystemLoadIndicator loadIndicator;
    private final PrometheusClient prometheusClient;
    private final NodeIdentity nodeIdentity;
    private final DeviceActivityBitmapRepository deviceActivityBitmapRepository;
    private final AtomicReference<CachedValue<RealtimeMetricsDTO>> realtimeCache = new AtomicReference<>();
    private final AtomicReference<CachedValue<List<HotProductDTO>>> hotProductsCache = new AtomicReference<>();
    private final ConcurrentMap<String, CachedValue<MonitorTrendsDTO>> trendsCache = new ConcurrentHashMap<>();

    public MonitorOverviewService(
            @Qualifier("systemLoadIndicatorImpl") SystemLoadIndicator loadIndicator,
            PrometheusClient prometheusClient,
            NodeIdentity nodeIdentity,
            DeviceActivityBitmapRepository deviceActivityBitmapRepository
    ) {
        this.loadIndicator = loadIndicator;
        this.prometheusClient = prometheusClient;
        this.nodeIdentity = nodeIdentity;
        this.deviceActivityBitmapRepository = deviceActivityBitmapRepository;
    }

    public RealtimeMetricsDTO getRealtimeMetrics() {
        Instant now = Instant.now();
        CachedValue<RealtimeMetricsDTO> cached = realtimeCache.get();
        if (isFresh(cached, now, REALTIME_CACHE_TTL)) {
            return cached.value();
        }

        RealtimeMetricsDTO metrics = buildRealtimeMetrics(now);
        realtimeCache.set(new CachedValue<>(metrics, now));
        return metrics;
    }

    public MonitorTrendsDTO getTrends(String range) {
        TrendWindow window = resolveWindow(range);
        Instant now = Instant.now();
        CachedValue<MonitorTrendsDTO> cached = trendsCache.get(window.range());
        if (isFresh(cached, now, TRENDS_CACHE_TTL)) {
            return cached.value();
        }

        MonitorTrendsDTO trends = buildTrends(window, now);
        trendsCache.put(window.range(), new CachedValue<>(trends, now));
        return trends;
    }

    private RealtimeMetricsDTO buildRealtimeMetrics(Instant now) {
        LoadSnapshot snapshot = loadIndicator.getSnapshot();

        ClusterNode node = ClusterBuilderSlot.getClusterNode(RESOURCE_NAME);
        int activeRequests = node != null ? (int) node.curThreadNum() : 0;
        long todayActiveDevices = deviceActivityBitmapRepository.countActive(LocalDate.now());
        double blockRate = getBlockRate(node);
        String circuitState = getCircuitState();
        String region = nodeIdentity.regionCode();
        String hostLabel = nodeIdentity.hostCode();

        HostMetricsDTO hostSummary = HostMetricsDTO.builder()
                .host(hostLabel)
                .cpuUsage(sanitize(snapshot.hostCpuUsage()))
                .memoryUsage(sanitize(snapshot.hostMemoryUsage()))
                .networkInBytes(sanitize(snapshot.networkInBytes()))
                .networkOutBytes(sanitize(snapshot.networkOutBytes()))
                .build();

        InstanceMetricsDTO instanceSummary = InstanceMetricsDTO.builder()
                .instance(nodeIdentity.monitoringInstanceLabel())
                .cpuUsage(snapshot.cpuUsage())
                .memoryUsage(snapshot.memoryUsage())
                .checkQps(sanitize(snapshot.checkQps()))
                .reportQps(sanitize(snapshot.reportQps()))
                .activeRequests(activeRequests)
                .blockRate(blockRate)
                .circuitState(circuitState)
                .build();

        return RealtimeMetricsDTO.builder()
                .loadScore(snapshot.totalScore())
                .loadLevel(snapshot.level().name())
                .cpuUsage(snapshot.cpuUsage())
                .memoryUsage(snapshot.memoryUsage())
                .checkQps(sanitize(snapshot.checkQps()))
                .reportQps(sanitize(snapshot.reportQps()))
                .checkP50Latency(sanitize(snapshot.checkP50Latency()))
                .checkP99Latency(sanitize(snapshot.checkP99Latency()))
                .reportP50Latency(sanitize(snapshot.reportP50Latency()))
                .reportP99Latency(sanitize(snapshot.reportP99Latency()))
                .activeRequests(activeRequests)
                .todayActiveDevices(todayActiveDevices)
                .blockRate(blockRate)
                .circuitState(circuitState)
                .region(region)
                .host(hostLabel)
                .hostCpuUsage(sanitize(snapshot.hostCpuUsage()))
                .hostMemoryUsage(sanitize(snapshot.hostMemoryUsage()))
                .networkInBytes(sanitize(snapshot.networkInBytes()))
                .networkOutBytes(sanitize(snapshot.networkOutBytes()))
                .hostSummary(hostSummary)
                .instanceSummary(instanceSummary)
                .hosts(getHosts())
                .instances(getInstances(blockRate, circuitState))
                .controlState(buildControlState(snapshot.level(), snapshot.totalScore()))
                .hotProducts(getHotProducts())
                .timestamp(now)
                .build();
    }

    private MonitorTrendsDTO buildTrends(TrendWindow window, Instant now) {
        String region = nodeIdentity.regionCode();
        long end = now.getEpochSecond();
        long start = end - window.durationSeconds();

        return MonitorTrendsDTO.builder()
                .range(window.range())
                .stepSeconds(window.stepSeconds())
                .checkQps(queryTrend(
                        String.format("sum(rate(fota_device_checks_total{region=\"%s\"}[5m]))", region),
                        start, end, window.prometheusStep()))
                .reportQps(queryTrend(
                        String.format("sum(rate(fota_upgrade_events_total{region=\"%s\"}[5m]))", region),
                        start, end, window.prometheusStep()))
                .checkP50Latency(queryTrend(
                        String.format("histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{region=\"%s\",uri=\"%s\"}[5m])) by (le)) * 1000", region, "/v1/upgrade/check"),
                        start, end, window.prometheusStep()))
                .checkP99Latency(queryTrend(
                        String.format("histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region=\"%s\",uri=\"%s\"}[5m])) by (le)) * 1000", region, "/v1/upgrade/check"),
                        start, end, window.prometheusStep()))
                .reportP50Latency(queryTrend(
                        String.format("histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{region=\"%s\",uri=\"%s\"}[5m])) by (le)) * 1000", region, "/v1/upgrade/report"),
                        start, end, window.prometheusStep()))
                .reportP99Latency(queryTrend(
                        String.format("histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{region=\"%s\",uri=\"%s\"}[5m])) by (le)) * 1000", region, "/v1/upgrade/report"),
                        start, end, window.prometheusStep()))
                .blockRate(queryTrend(
                        String.format("sum(rate(fota_rate_limited_total{region=\"%s\",resource=\"%s\"}[5m])) / clamp_min(sum(rate(fota_device_checks_total{region=\"%s\"}[5m])), 1)", region, RESOURCE_NAME, region),
                        start, end, window.prometheusStep()))
                .activeDevicesTotal(queryTrend(
                        String.format("max without(instance) (fota_active_devices_today{region=\"%s\"})", region),
                        start, end, window.prometheusStep()))
                .activeDevicesIncrement(queryTrend(
                        String.format("clamp_min(max without(instance) (fota_active_devices_today{region=\"%s\"} - fota_active_devices_today{region=\"%s\"} offset 5m), 0)", region, region),
                        start, end, window.prometheusStep()))
                .build();
    }

    public List<HotProductDTO> getHotProducts() {
        Instant now = Instant.now();
        CachedValue<List<HotProductDTO>> cached = hotProductsCache.get();
        if (isFresh(cached, now, REALTIME_CACHE_TTL)) {
            return cached.value();
        }

        List<HotProductDTO> hotProducts = buildHotProducts();
        hotProductsCache.set(new CachedValue<>(hotProducts, now));
        return hotProducts;
    }

    private List<HotProductDTO> buildHotProducts() {
        String region = nodeIdentity.regionCode();
        List<PrometheusClient.MetricSample> checks = prometheusClient.getTopProductsByCheckQps(region, 5);
        List<PrometheusClient.MetricSample> reports = prometheusClient.getTopProductsByReportQps(region, 5);
        double totalCheckQps = Math.max(prometheusClient.getRegionCheckQps(region), 0.0);

        Map<String, Double> reportByProduct = new HashMap<>();
        for (PrometheusClient.MetricSample sample : reports) {
            reportByProduct.put(sample.metric().getOrDefault("product", "unknown"), sample.value());
        }

        return checks.stream()
                .map(sample -> toHotProduct(sample, reportByProduct.getOrDefault(sample.metric().getOrDefault("product", "unknown"), 0.0), totalCheckQps))
                .toList();
    }

    private HotProductDTO toHotProduct(PrometheusClient.MetricSample sample, double reportQps, double totalCheckQps) {
        String productModel = sample.metric().getOrDefault("product", "unknown");
        double checkQps = sample.value();
        double trafficShare = totalCheckQps > 0 ? checkQps / totalCheckQps : 0.0;

        return HotProductDTO.builder()
                .product(productModel)
                .checkQps(checkQps)
                .reportQps(reportQps)
                .trafficShare(trafficShare)
                .build();
    }

    private ControlStateDTO buildControlState(LoadLevel level, int loadScore) {
        String region = nodeIdentity.regionCode();
        return ControlStateDTO.builder()
                .region(region)
                .loadScore(loadScore)
                .loadLevel(level.name())
                .recommendedMultiplier(recommendedMultiplier(level))
                .build();
    }

    private List<TrendPointDTO> queryTrend(String query, long start, long end, String step) {
        return prometheusClient.queryRange(query, start, end, step)
                .orElse(List.of())
                .stream()
                .map(point -> TrendPointDTO.builder()
                        .timestamp(point.timestamp())
                        .value(sanitize(point.value()))
                        .build())
                .toList();
    }

    private TrendWindow resolveWindow(String range) {
        return switch (range == null ? "" : range.trim().toLowerCase()) {
            case "15m" -> new TrendWindow("15m", 15 * 60L, 30, "30s");
            case "6h" -> new TrendWindow("6h", 6 * 60 * 60L, 300, "5m");
            case "24h" -> new TrendWindow("24h", 24 * 60 * 60L, 900, "15m");
            default -> new TrendWindow("1h", ChronoUnit.HOURS.getDuration().toSeconds(), 60, "1m");
        };
    }

    private List<HostMetricsDTO> getHosts() {
        Map<String, HostMetricsDTO.HostMetricsDTOBuilder> builders = new HashMap<>();

        mergeHosts(builders, prometheusClient.getHostsCpuUsage(), (builder, value) -> builder.cpuUsage(value));
        mergeHosts(builders, prometheusClient.getHostsMemoryUsage(), (builder, value) -> builder.memoryUsage(value));
        mergeHosts(builders, prometheusClient.getHostsNetworkInBytes(), (builder, value) -> builder.networkInBytes(value));
        mergeHosts(builders, prometheusClient.getHostsNetworkOutBytes(), (builder, value) -> builder.networkOutBytes(value));

        return builders.values().stream()
                .map(HostMetricsDTO.HostMetricsDTOBuilder::build)
                .toList();
    }

    private List<InstanceMetricsDTO> getInstances(double blockRate, String circuitState) {
        String region = nodeIdentity.regionCode();
        Map<String, InstanceMetricsDTO.InstanceMetricsDTOBuilder> builders = new HashMap<>();
        mergeInstances(builders, prometheusClient.getInstancesCpuUsage(region), (builder, value) -> builder.cpuUsage(value));
        mergeInstances(builders, prometheusClient.getInstancesHeapUsage(region), (builder, value) -> builder.memoryUsage(value));
        mergeInstances(builders, prometheusClient.getInstancesCheckQps(region, 10), (builder, value) -> builder.checkQps(value));
        mergeInstances(builders, prometheusClient.getInstancesReportQps(region, 10), (builder, value) -> builder.reportQps(value));

        List<InstanceMetricsDTO> instances = new ArrayList<>();
        for (InstanceMetricsDTO.InstanceMetricsDTOBuilder builder : builders.values()) {
            InstanceMetricsDTO dto = builder
                    .blockRate(blockRate)
                    .circuitState(circuitState)
                    .build();
            instances.add(dto);
        }
        return instances;
    }

    private double recommendedMultiplier(LoadLevel level) {
        return switch (level) {
            case LOW -> 0.9;
            case NORMAL -> 1.0;
            case HIGH -> 1.6;
            case CRITICAL -> 3.2;
        };
    }

    private double getBlockRate(ClusterNode node) {
        if (node != null && node.totalQps() > 0) {
            return node.blockQps() / node.totalQps();
        }
        return prometheusClient.getBlockRate(nodeIdentity.regionCode(), RESOURCE_NAME);
    }

    private String getCircuitState() {
        List<DegradeRule> rules = DegradeRuleManager.getRules();
        boolean hasRule = rules.stream().anyMatch(r -> RESOURCE_NAME.equals(r.getResource()));
        return hasRule ? "CLOSED" : "UNKNOWN";
    }

    private double sanitize(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0;
        }
        return value;
    }

    private <T> boolean isFresh(CachedValue<T> cached, Instant now, Duration ttl) {
        return cached != null && Duration.between(cached.cachedAt(), now).compareTo(ttl) < 0;
    }

    private void mergeHosts(
            Map<String, HostMetricsDTO.HostMetricsDTOBuilder> builders,
            List<PrometheusClient.MetricSample> samples,
            java.util.function.BiConsumer<HostMetricsDTO.HostMetricsDTOBuilder, Double> consumer) {
        for (PrometheusClient.MetricSample sample : samples) {
            String host = sample.metric().getOrDefault("host", sample.metric().getOrDefault("instance", "unknown"));
            HostMetricsDTO.HostMetricsDTOBuilder builder = builders.computeIfAbsent(host,
                    key -> HostMetricsDTO.builder().host(key));
            consumer.accept(builder, sample.value());
        }
    }

    private void mergeInstances(
            Map<String, InstanceMetricsDTO.InstanceMetricsDTOBuilder> builders,
            List<PrometheusClient.MetricSample> samples,
            java.util.function.BiConsumer<InstanceMetricsDTO.InstanceMetricsDTOBuilder, Double> consumer) {
        for (PrometheusClient.MetricSample sample : samples) {
            String instance = sample.metric().getOrDefault("instance", "unknown");
            InstanceMetricsDTO.InstanceMetricsDTOBuilder builder = builders.computeIfAbsent(instance,
                    key -> InstanceMetricsDTO.builder().instance(key));
            consumer.accept(builder, sample.value());
        }
    }

    private record TrendWindow(String range, long durationSeconds, int stepSeconds, String prometheusStep) {
    }

    private record CachedValue<T>(T value, Instant cachedAt) {
    }
}
