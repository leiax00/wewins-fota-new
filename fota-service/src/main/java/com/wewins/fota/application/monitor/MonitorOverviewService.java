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
import java.time.format.DateTimeFormatter;
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

    // ==================== 全局监控 API 方法 ====================

    /**
     * 获取全局监控指标
     * <p>
     * 当前实现：单区域模式下返回当前区域的包装数据
     * 多区域模式下需要从配置中心或其他服务获取其他区域数据
     *
     * @return 全系统监控指标
     */
    public GlobalMonitorMetricsDTO getGlobalMetrics() {
        Instant now = Instant.now();
        RealtimeMetricsDTO realtimeMetrics = getRealtimeMetrics();
        String region = nodeIdentity.regionCode();

        // 构建全局摘要（单区域模式）
        GlobalSummaryDTO globalSummary = GlobalSummaryDTO.builder()
                .loadScore(realtimeMetrics.getLoadScore())
                .loadLevel(realtimeMetrics.getLoadLevel())
                .checkQps(realtimeMetrics.getCheckQps())
                .reportQps(realtimeMetrics.getReportQps())
                .checkP50Latency(realtimeMetrics.getCheckP50Latency())
                .checkP99Latency(realtimeMetrics.getCheckP99Latency())
                .todayActiveDevices(realtimeMetrics.getTodayActiveDevices())
                .blockRate(realtimeMetrics.getBlockRate())
                .regionCount(1) // 单区域模式
                .totalInstances(realtimeMetrics.getInstances() != null ? realtimeMetrics.getInstances().size() : 1)
                .build();

        // 构建区域指标列表
        RegionMetricsDTO regionMetrics = buildRegionMetrics(realtimeMetrics, region);
        List<RegionMetricsDTO> regions = List.of(regionMetrics);

        // 构建增强的主机指标
        List<HostMetricsEnhancedDTO> enhancedHosts = buildEnhancedHosts(realtimeMetrics, region);

        // 构建增强的实例指标
        List<InstanceMetricsEnhancedDTO> enhancedInstances = buildEnhancedInstances(realtimeMetrics, region);

        // 构建增强的热点产品指标
        List<HotProductMetricsEnhancedDTO> enhancedHotProducts = buildEnhancedHotProducts(region);

        return GlobalMonitorMetricsDTO.builder()
                .global(globalSummary)
                .regions(regions)
                .hosts(enhancedHosts)
                .instances(enhancedInstances)
                .hotProducts(enhancedHotProducts)
                .timestamp(now)
                .build();
    }

    /**
     * 获取区域详情
     *
     * @param region 区域代码
     * @return 区域详情
     */
    public RegionDetailDTO getRegionDetail(String region) {
        Instant now = Instant.now();
        RealtimeMetricsDTO realtimeMetrics = getRealtimeMetrics();

        // 如果请求的区域不是当前区域，返回空数据
        if (!region.equals(nodeIdentity.regionCode())) {
            return RegionDetailDTO.builder()
                    .summary(RegionMetricsDTO.builder()
                            .region(region)
                            .loadScore(0)
                            .loadLevel("UNKNOWN")
                            .instanceCount(0)
                            .hotProductCount(0)
                            .build())
                    .hosts(List.of())
                    .instances(List.of())
                    .hotProducts(List.of())
                    .build();
        }

        RegionMetricsDTO summary = buildRegionMetrics(realtimeMetrics, region);

        return RegionDetailDTO.builder()
                .summary(summary)
                .hosts(realtimeMetrics.getHosts() != null ? realtimeMetrics.getHosts() : List.of())
                .instances(realtimeMetrics.getInstances() != null ? realtimeMetrics.getInstances() : List.of())
                .hotProducts(realtimeMetrics.getHotProducts() != null ? realtimeMetrics.getHotProducts() : List.of())
                .build();
    }

    /**
     * 获取实例详情
     *
     * @param instance 实例标识
     * @return 实例详情
     */
    public InstanceDetailDTO getInstanceDetail(String instance) {
        Instant now = Instant.now();
        RealtimeMetricsDTO realtimeMetrics = getRealtimeMetrics();
        String region = nodeIdentity.regionCode();
        String host = nodeIdentity.hostCode();

        // 查找请求的实例
        InstanceMetricsDTO targetInstance = null;
        if (realtimeMetrics.getInstances() != null) {
            for (InstanceMetricsDTO inst : realtimeMetrics.getInstances()) {
                if (instance.equals(inst.getInstance())) {
                    targetInstance = inst;
                    break;
                }
            }
        }

        // 如果未找到，返回当前实例
        if (targetInstance == null) {
            targetInstance = realtimeMetrics.getInstanceSummary();
        }

        // 构建增强的实例摘要
        InstanceMetricsEnhancedDTO summary = InstanceMetricsEnhancedDTO.builder()
                .instance(targetInstance.getInstance())
                .region(region)
                .host(host)
                .loadScore(realtimeMetrics.getLoadScore())
                .loadLevel(realtimeMetrics.getLoadLevel())
                .cpuUsage(targetInstance.getCpuUsage())
                .memoryUsage(targetInstance.getMemoryUsage())
                .checkQps(targetInstance.getCheckQps())
                .reportQps(targetInstance.getReportQps())
                .checkP50Latency(realtimeMetrics.getCheckP50Latency())
                .checkP99Latency(realtimeMetrics.getCheckP99Latency())
                .reportP50Latency(realtimeMetrics.getReportP50Latency())
                .reportP99Latency(realtimeMetrics.getReportP99Latency())
                .activeRequests(targetInstance.getActiveRequests())
                .blockRate(targetInstance.getBlockRate())
                .circuitState(targetInstance.getCircuitState())
                .build();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault());

        return InstanceDetailDTO.builder()
                .summary(summary)
                .hostCpuUsage(realtimeMetrics.getHostCpuUsage())
                .hostMemoryUsage(realtimeMetrics.getHostMemoryUsage())
                .hotProducts(realtimeMetrics.getHotProducts() != null ? realtimeMetrics.getHotProducts() : List.of())
                .region(region)
                .host(host)
                .instance(targetInstance.getInstance())
                .lastRefreshTime(formatter.format(now))
                .build();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建区域指标
     */
    private RegionMetricsDTO buildRegionMetrics(RealtimeMetricsDTO realtimeMetrics, String region) {
        return RegionMetricsDTO.builder()
                .region(region)
                .loadScore(realtimeMetrics.getLoadScore())
                .loadLevel(realtimeMetrics.getLoadLevel())
                .checkQps(realtimeMetrics.getCheckQps())
                .reportQps(realtimeMetrics.getReportQps())
                .checkP50Latency(realtimeMetrics.getCheckP50Latency())
                .checkP99Latency(realtimeMetrics.getCheckP99Latency())
                .reportP50Latency(realtimeMetrics.getReportP50Latency())
                .reportP99Latency(realtimeMetrics.getReportP99Latency())
                .todayActiveDevices(realtimeMetrics.getTodayActiveDevices())
                .blockRate(realtimeMetrics.getBlockRate())
                .instanceCount(realtimeMetrics.getInstances() != null ? realtimeMetrics.getInstances().size() : 1)
                .hotProductCount(realtimeMetrics.getHotProducts() != null ? realtimeMetrics.getHotProducts().size() : 0)
                .build();
    }

    /**
     * 构建增强的主机指标列表
     */
    private List<HostMetricsEnhancedDTO> buildEnhancedHosts(RealtimeMetricsDTO realtimeMetrics, String region) {
        List<HostMetricsDTO> hosts = realtimeMetrics.getHosts();
        if (hosts == null || hosts.isEmpty()) {
            return List.of();
        }

        // 统计每个主机的实例数量
        Map<String, Integer> instanceCountByHost = new HashMap<>();
        List<InstanceMetricsDTO> instances = realtimeMetrics.getInstances();
        if (instances != null) {
            for (InstanceMetricsDTO instance : instances) {
                String instanceName = instance.getInstance();
                // 从实例名提取主机名（格式通常是 "host:port" 或类似格式）
                String host = extractHostFromInstance(instanceName);
                instanceCountByHost.merge(host, 1, Integer::sum);
            }
        }

        String currentHost = nodeIdentity.hostCode();
        return hosts.stream()
                .map(host -> HostMetricsEnhancedDTO.builder()
                        .host(host.getHost())
                        .region(region)
                        .cpuUsage(host.getCpuUsage())
                        .memoryUsage(host.getMemoryUsage())
                        .networkInBytes(host.getNetworkInBytes())
                        .networkOutBytes(host.getNetworkOutBytes())
                        .instanceCount(instanceCountByHost.getOrDefault(host.getHost(), 1))
                        .build())
                .toList();
    }

    /**
     * 构建增强的实例指标列表
     */
    private List<InstanceMetricsEnhancedDTO> buildEnhancedInstances(RealtimeMetricsDTO realtimeMetrics, String region) {
        List<InstanceMetricsDTO> instances = realtimeMetrics.getInstances();
        if (instances == null || instances.isEmpty()) {
            // 返回当前实例
            InstanceMetricsDTO current = realtimeMetrics.getInstanceSummary();
            return List.of(InstanceMetricsEnhancedDTO.builder()
                    .instance(current.getInstance())
                    .region(region)
                    .host(nodeIdentity.hostCode())
                    .loadScore(realtimeMetrics.getLoadScore())
                    .loadLevel(realtimeMetrics.getLoadLevel())
                    .cpuUsage(current.getCpuUsage())
                    .memoryUsage(current.getMemoryUsage())
                    .checkQps(current.getCheckQps())
                    .reportQps(current.getReportQps())
                    .checkP50Latency(realtimeMetrics.getCheckP50Latency())
                    .checkP99Latency(realtimeMetrics.getCheckP99Latency())
                    .reportP50Latency(realtimeMetrics.getReportP50Latency())
                    .reportP99Latency(realtimeMetrics.getReportP99Latency())
                    .activeRequests(current.getActiveRequests())
                    .blockRate(current.getBlockRate())
                    .circuitState(current.getCircuitState())
                    .build());
        }

        String host = nodeIdentity.hostCode();
        return instances.stream()
                .map(instance -> InstanceMetricsEnhancedDTO.builder()
                        .instance(instance.getInstance())
                        .region(region)
                        .host(host)
                        .loadScore(realtimeMetrics.getLoadScore())
                        .loadLevel(realtimeMetrics.getLoadLevel())
                        .cpuUsage(instance.getCpuUsage())
                        .memoryUsage(instance.getMemoryUsage())
                        .checkQps(instance.getCheckQps())
                        .reportQps(instance.getReportQps())
                        .checkP50Latency(realtimeMetrics.getCheckP50Latency())
                        .checkP99Latency(realtimeMetrics.getCheckP99Latency())
                        .reportP50Latency(realtimeMetrics.getReportP50Latency())
                        .reportP99Latency(realtimeMetrics.getReportP99Latency())
                        .activeRequests(instance.getActiveRequests())
                        .blockRate(instance.getBlockRate())
                        .circuitState(instance.getCircuitState())
                        .build())
                .toList();
    }

    /**
     * 构建增强的热点产品指标列表
     */
    private List<HotProductMetricsEnhancedDTO> buildEnhancedHotProducts(String region) {
        List<HotProductDTO> hotProducts = getHotProducts();
        if (hotProducts == null || hotProducts.isEmpty()) {
            return List.of();
        }

        return hotProducts.stream()
                .map(product -> HotProductMetricsEnhancedDTO.builder()
                        .product(product.getProduct())
                        .checkQps(product.getCheckQps())
                        .reportQps(product.getReportQps())
                        .trafficShare(product.getTrafficShare())
                        .activeRegionCount(1) // 单区域模式
                        .build())
                .toList();
    }

    /**
     * 从实例标识中提取主机名
     */
    private String extractHostFromInstance(String instance) {
        if (instance == null) {
            return "unknown";
        }
        // 实例名格式通常是 "host:port" 或 "host"
        int colonIndex = instance.indexOf(':');
        if (colonIndex > 0) {
            return instance.substring(0, colonIndex);
        }
        return instance;
    }
}
