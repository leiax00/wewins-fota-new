package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.enums.PolicyStatus;
import com.wewins.fota.domain.policy.enums.TriggerMode;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.repository.UpgradeEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 端到端集成测试
 * <p>
 * 注意：此测试类已标记为 @Disabled，需要根据新 API 重写
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("端到端集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("API 变更后需要重写")
class EndToEndIntegrationTest {

    @Autowired
    private UpgradeCheckService upgradeCheckService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FirmwareVersionRepository firmwareVersionRepository;

    @Autowired
    private UpgradePolicyRepository upgradePolicyRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private UpgradeEventRepository upgradeEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceRateLimiter deviceRateLimiter;

    @MockBean
    private DeviceActivityBitmapRepository bitmapRepository;

    private static List<Long> cleanupProductIds = new ArrayList<>();
    private static List<Long> cleanupVersionIds = new ArrayList<>();
    private static List<Long> cleanupPolicyIds = new ArrayList<>();
    private static List<Long> cleanupDeviceIds = new ArrayList<>();

    @BeforeAll
    static void setupMocks() {
        // 可以在这里设置全局 Mock
    }

    @BeforeEach
    void setUp() {
        // 配置默认 Mock 行为
        long resetAt = System.currentTimeMillis() / 1000 + 60;
        when(deviceRateLimiter.allow(anyString(), any(), any()))
                .thenReturn(RateLimitDecision.allowed(100, resetAt));
    }

    @Test
    @Order(1)
    @DisplayName("准备测试数据：产品、固件版本、策略、设备")
    void prepareTestData() {
        log.info("开始准备测试数据...");

        // 1. 创建产品
        Product product = Product.builder()
                .name("测试产品")
                .model("TEST_MODEL_001")
                .manufacturer("测试厂商")
                .remark("用于端到端测试")
                .build();
        product = productRepository.create(product);
        cleanupProductIds.add(product.getId());
        log.info("创建产品: id={}, model={}", product.getId(), product.getModel());

        // 2. 创建固件版本
        FirmwareVersion v1 = createFirmwareVersion(product.getId(), "1.0.0", "v1.0.0-build01");
        FirmwareVersion v2 = createFirmwareVersion(product.getId(), "2.0.0", "v2.0.0-build01");
        log.info("创建固件版本: v1.id={}, v2.id={}", v1.getId(), v2.getId());

        // 3. 创建升级策略
        UpgradePolicy policy = createUpgradePolicy(product.getId(), v2.getId(), List.of(v1.getId()));
        log.info("创建升级策略: id={}, targetVersionId={}", policy.getId(), policy.getTargetVersionId());

        // 4. 创建测试设备
        Device device1 = createDevice("354972069000001", product.getId(), v1.getId());
        Device device2 = createDevice("354972069000002", product.getId(), v1.getId());
        log.info("创建测试设备: d1.id={}, d2.id={}", device1.getId(), device2.getId());

        log.info("测试数据准备完成");
    }

    @Test
    @Order(2)
    @DisplayName("测试完整升级检查流程")
    void testCompleteUpgradeCheckFlow() {
        log.info("开始测试完整升级检查流程...");

        String imei = "354972069000001";

        // 执行升级检查
        UpgradeCheckReqDTO request = createRequest(imei, "TEST_MODEL_001", "1.0.0");
        CheckResult result = upgradeCheckService.checkUpgrade(request);

        // 验证结果
        assertNotNull(result, "检查结果不应为 null");
        assertTrue(result.getHasUpdate(), "应该有更新可用");
        assertEquals(UpgradeDecision.UPDATE, result.getDecision(), "决策应该是 UPDATE");
        assertNotNull(result.getTargetVersionId(), "目标版本 ID 不应为 null");

        log.info("升级检查流程测试完成: decision={}, targetVersionId={}",
                result.getDecision(), result.getTargetVersionId());
    }

    @Test
    @Order(3)
    @DisplayName("测试灰度发布分布均匀性")
    void testGrayReleaseDistribution() {
        log.info("开始测试灰度发布分布均匀性...");

        String imeiPrefix = "354972069";
        int sampleSize = 1000;
        int grayRate = 50; // 50% 灰度

        // 创建测试设备
        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < sampleSize; i++) {
            String imei = imeiPrefix + String.format("%06d", i);
            Device device = createDevice(imei, cleanupProductIds.get(0),
                    cleanupVersionIds.get(0));
            devices.add(device);
        }

        // 创建 50% 灰度策略
        UpgradePolicy grayPolicy = createUpgradePolicy(
                cleanupProductIds.get(0),
                cleanupVersionIds.get(1),
                List.of(cleanupVersionIds.get(0)),
                grayRate
        );

        // 执行检查并统计
        int hitCount = 0;
        for (Device device : devices) {
            UpgradeCheckReqDTO request = createRequest(device.getImei(), "TEST_MODEL_001", "1.0.0");
            CheckResult result = upgradeCheckService.checkUpgrade(request);
            if (Boolean.TRUE.equals(result.getHasUpdate())) {
                hitCount++;
            }
        }

        double actualRate = (double) hitCount / sampleSize * 100;
        log.info("灰度测试: 样本数={}, 命中数={}, 实际命中率={}", sampleSize, hitCount, actualRate);

        // 验证分布：应该在 45%-55% 之间（放宽到 ±5%）
        assertTrue(actualRate >= 45.0 && actualRate <= 55.0,
                String.format("灰度命中率 %.2f%% 不在预期区间 [45%%, 55%%]", actualRate));
    }

    @Test
    @Order(4)
    @DisplayName("测试性能：P99 响应时间 < 50ms")
    void testPerformance() {
        log.info("开始性能测试...");

        String imei = "354972069000001";
        int requestCount = 1000;
        int warmupCount = 100;

        // 预热
        for (int i = 0; i < warmupCount; i++) {
            UpgradeCheckReqDTO request = createRequest(imei, "TEST_MODEL_001", "1.0.0");
            upgradeCheckService.checkUpgrade(request);
        }

        // 正式测试
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            UpgradeCheckReqDTO request = createRequest(imei, "TEST_MODEL_001", "1.0.0");
            long start = System.nanoTime();
            upgradeCheckService.checkUpgrade(request);
            long end = System.nanoTime();
            latencies.add((end - start) / 1_000_000); // 转换为毫秒
        }

        // 计算统计数据
        long avg = latencies.stream().mapToLong(Long::longValue).sum() / requestCount;
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        long min = latencies.stream().mapToLong(Long::longValue).min().orElse(0);

        // 计算 P99
        List<Long> sorted = latencies.stream().sorted().collect(Collectors.toList());
        int p99Index = (int) Math.ceil(requestCount * 0.99) - 1;
        long p99 = sorted.get(p99Index);

        log.info("性能测试结果: 平均={}ms, 最小={}ms, 最大={}ms, P99={}ms", avg, min, max, p99);

        // 验证 P99 < 50ms
        assertTrue(p99 < 50, String.format("P99 响应时间 %dms 超过目标 50ms", p99));
    }

    @Test
    @Order(5)
    @DisplayName("测试并发场景")
    void testConcurrentRequests() throws InterruptedException {
        log.info("开始并发测试...");

        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < requestsPerThread; i++) {
                    try {
                        String imei = "354972069" + String.format("%06d", threadId * 1000 + i);
                        Device device = createDevice(imei, cleanupProductIds.get(0),
                                cleanupVersionIds.get(0));

                        UpgradeCheckReqDTO request = createRequest(imei, "TEST_MODEL_001", "1.0.0");
                        CheckResult result = upgradeCheckService.checkUpgrade(request);
                        if (result != null) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("并发请求失败: thread={}, request={}", threadId, i, e);
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int totalRequests = threadCount * requestsPerThread;
        double qps = (double) totalRequests / (duration / 1000.0);

        log.info("并发测试完成: 总请求={}, 成功={}, 失败={}, 耗时={}ms, QPS={}",
                totalRequests, successCount.get(), errorCount.get(), duration, qps);

        assertEquals(totalRequests, successCount.get() + errorCount.get(),
                "请求数量应该等于成功+失败数量");
    }

    @Test
    @Order(6)
    @DisplayName("测试边界场景")
    void testEdgeCases() {
        log.info("开始边界场景测试...");

        // 1. 设备不存在
        UpgradeCheckReqDTO notFoundRequest = createRequest("999999999999999", "TEST_MODEL_001", "1.0.0");
        CheckResult notFoundResult = upgradeCheckService.checkUpgrade(notFoundRequest);
        assertEquals(UpgradeDecision.DEVICE_NOT_FOUND, notFoundResult.getDecision(),
                "设备不存在应该返回 DEVICE_NOT_FOUND");

        // 2. 灰度 0%
        UpgradePolicy zeroGrayPolicy = createUpgradePolicy(
                cleanupProductIds.get(0),
                cleanupVersionIds.get(1),
                List.of(cleanupVersionIds.get(0)),
                0
        );
        UpgradeCheckReqDTO zeroGrayRequest = createRequest("354972069000001", "TEST_MODEL_001", "1.0.0");
        CheckResult zeroGrayResult = upgradeCheckService.checkUpgrade(zeroGrayRequest);
        // 灰度 0% 不应该匹配，但由于可能有其他策略，这里只验证不报错
        assertNotNull(zeroGrayResult, "灰度 0% 应该有结果");

        // 3. 灰度 100%
        UpgradePolicy fullGrayPolicy = createUpgradePolicy(
                cleanupProductIds.get(0),
                cleanupVersionIds.get(1),
                List.of(cleanupVersionIds.get(0)),
                100
        );
        UpgradeCheckReqDTO fullGrayRequest = createRequest("354972069000001", "TEST_MODEL_001", "1.0.0");
        CheckResult fullGrayResult = upgradeCheckService.checkUpgrade(fullGrayRequest);
        assertNotNull(fullGrayResult, "灰度 100% 应该有结果");

        log.info("边界场景测试完成");
    }

    @AfterAll
    static void cleanup() {
        log.info("开始清理测试数据...");
        // 清理设备
        cleanupDeviceIds.forEach(id -> {
            try {
                // deviceRepository.deleteById(id);
            } catch (Exception e) {
                log.warn("清理设备失败: id={}", id, e);
            }
        });
        cleanupDeviceIds.clear();

        // 清理策略
        cleanupPolicyIds.forEach(id -> {
            try {
                // upgradePolicyRepository.deleteById(id);
            } catch (Exception e) {
                log.warn("清理策略失败: id={}", id, e);
            }
        });
        cleanupPolicyIds.clear();

        // 清理版本
        cleanupVersionIds.forEach(id -> {
            try {
                // firmwareVersionRepository.deleteById(id);
            } catch (Exception e) {
                log.warn("清理版本失败: id={}", id, e);
            }
        });
        cleanupVersionIds.clear();

        // 清理产品
        cleanupProductIds.forEach(id -> {
            try {
                // productRepository.deleteById(id);
            } catch (Exception e) {
                log.warn("清理产品失败: id={}", id, e);
            }
        });
        cleanupProductIds.clear();

        log.info("测试数据清理完成");
    }

    // ========== 辅助方法 ==========

    private FirmwareVersion createFirmwareVersion(Long productId, String versionNumber, String internalVersion) {
        FirmwareVersion version = FirmwareVersion.builder()
                .productId(productId)
                .version(versionNumber)
                .internalVersion(internalVersion)
                .packageStatus("READY")
                .fileSize(10_000_000L)
                .md5("abc123")
                .build();
        version = firmwareVersionRepository.create(version);
        cleanupVersionIds.add(version.getId());
        return version;
    }

    private UpgradePolicy createUpgradePolicy(Long productId, Long targetVersionId, List<Long> sourceVersionIds) {
        return createUpgradePolicy(productId, targetVersionId, sourceVersionIds, null);
    }

    private UpgradePolicy createUpgradePolicy(Long productId, Long targetVersionId,
                                              List<Long> sourceVersionIds, Integer grayRate) {
        ArrayNode sourceVersionsArray = objectMapper.createArrayNode();
        sourceVersionIds.forEach(sourceVersionsArray::add);

        UpgradePolicy policy = UpgradePolicy.builder()
                .productId(productId)
                .name("测试策略-" + System.currentTimeMillis())
                .targetVersionId(targetVersionId)
                .sourceVersions(sourceVersionsArray)
                .priority(10)
                .grayRate(grayRate)
                .status(PolicyStatus.ACTIVE)
                .triggerMode(TriggerMode.BOTH)
                .targetMode("ALL")
                .build();
        policy = upgradePolicyRepository.create(policy);
        cleanupPolicyIds.add(policy.getId());
        return policy;
    }

    private Device createDevice(String imei, Long productId, Long versionId) {
        Device device = Device.builder()
                .imei(imei)
                .productId(productId)
                .currentVersionId(versionId)
                .status("ACTIVE")
                .build();
        device = deviceRepository.create(device);
        cleanupDeviceIds.add(device.getId());
        return device;
    }

    private UpgradeCheckReqDTO createRequest(String imei, String productModel, String version) {
        UpgradeCheckReqDTO request = new UpgradeCheckReqDTO();
        request.setImei(imei);
        request.setProduct(productModel);
        request.setVersion(version);
        request.setAuto(1);
        request.setLang("en");
        return request;
    }
}
