package com.wewins.fota.application.monitor;

import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.wewins.fota.adapter.api.admin.dto.ControlStateDTO;
import com.wewins.fota.adapter.api.admin.dto.HostMetricsDTO;
import com.wewins.fota.adapter.api.admin.dto.HotProductDTO;
import com.wewins.fota.adapter.api.admin.dto.InstanceMetricsDTO;
import com.wewins.fota.adapter.api.admin.dto.RealtimeMetricsDTO;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.enums.ProductPriority;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.metrics.NodeIdentity;
import com.wewins.fota.infra.metrics.PrometheusClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitorOverviewService {

    private static final String RESOURCE_NAME = "upgrade:check";
    private static final int DEFAULT_AUTO_INTERVAL = 86400;
    private static final int DEFAULT_MANUAL_INTERVAL = 3600;

    private final SystemLoadIndicator loadIndicator;
    private final PrometheusClient prometheusClient;
    private final ProductRepository productRepository;
    private final ControlParameterRepository controlParameterRepository;
    private final NodeIdentity nodeIdentity;

    public RealtimeMetricsDTO getRealtimeMetrics() {
        LoadSnapshot snapshot = loadIndicator.getSnapshot();

        ClusterNode node = ClusterBuilderSlot.getClusterNode(RESOURCE_NAME);
        int activeRequests = node != null ? (int) node.curThreadNum() : 0;
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
                .currentQps(snapshot.qps())
                .checkQps(sanitize(snapshot.checkQps()))
                .reportQps(sanitize(snapshot.reportQps()))
                .p99Latency(snapshot.p99Latency())
                .activeRequests(activeRequests)
                .blockRate(blockRate)
                .circuitState(circuitState)
                .build();

        return RealtimeMetricsDTO.builder()
                .loadScore(snapshot.totalScore())
                .loadLevel(snapshot.level().name())
                .cpuUsage(snapshot.cpuUsage())
                .memoryUsage(snapshot.memoryUsage())
                .currentQps(snapshot.qps())
                .checkQps(sanitize(snapshot.checkQps()))
                .reportQps(sanitize(snapshot.reportQps()))
                .p99Latency(snapshot.p99Latency())
                .activeRequests(activeRequests)
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
                .timestamp(Instant.now())
                .build();
    }

    public List<HotProductDTO> getHotProducts() {
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
        String productCode = sample.metric().getOrDefault("product", "unknown");
        ControlParameter controlParameter = productRepository.findByModel(productCode)
                .flatMap(product -> controlParameterRepository.getByProduct(product.getId()))
                .orElse(ControlParameter.createProductDefault(null));

        double checkQps = sample.value();
        double trafficShare = totalCheckQps > 0 ? checkQps / totalCheckQps : 0.0;
        ProductPriority priority = controlParameter.getPriority() != null
                ? controlParameter.getPriority()
                : ProductPriority.NORMAL;

        return HotProductDTO.builder()
                .product(productCode)
                .checkQps(checkQps)
                .reportQps(reportQps)
                .trafficShare(trafficShare)
                .priority(priority)
                .intervalBias(controlParameter.getIntervalBias() != null ? controlParameter.getIntervalBias() : priority.getIntervalBias())
                .hotspotProtectionEnabled(Boolean.TRUE.equals(controlParameter.getHotspotProtectionEnabled()))
                .build();
    }

    private ControlStateDTO buildControlState(LoadLevel level, int loadScore) {
        String region = nodeIdentity.regionCode();
        return ControlStateDTO.builder()
                .region(region)
                .loadScore(loadScore)
                .loadLevel(level.name())
                .recommendedMultiplier(recommendedMultiplier(level))
                .baseAutoInterval(DEFAULT_AUTO_INTERVAL)
                .baseManualInterval(DEFAULT_MANUAL_INTERVAL)
                .build();
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
            dto.setCurrentQps(dto.getCheckQps() + dto.getReportQps());
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
        return value < 0 ? 0 : value;
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
}
