package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.reporting.DeviceCheckLogBuilder;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.device.service.DeviceInfoUpdateGateway;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
import com.wewins.fota.infra.metrics.FotaMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyInt;

/**
 * UpgradeCheckService 灰度发布检查测试
 * <p>
 * 验证灰度检查集成到策略匹配流程中
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("UpgradeCheckService 灰度检查测试")
@ExtendWith(MockitoExtension.class)
class UpgradeCheckServiceGrayTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCacheRepository deviceCacheRepository;
    @Mock
    private DataIntegrityService dataIntegrityService;
    @Mock
    private UpgradePolicyRepository upgradePolicyRepository;
    @Mock
    private DeviceRateLimiter deviceRateLimiter;
    @Mock
    private DeviceActivityBitmapRepository bitmapRepository;
    @Mock
    private PolicyMatcher policyMatcher;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;
    @Mock
    private FirmwareVersionLookupService firmwareVersionLookupService;
    @Mock
    private GrayReleaseService grayReleaseService;
    @Mock
    private UpgradeResponseBuilder upgradeResponseBuilder;
    @Mock
    private UpgradeRequestValidator requestValidator;
    @Mock
    private CheckLogGateway checkLogGateway;
    @Mock
    private DeviceCheckLogBuilder checkLogBuilder;
    @Mock
    private DeviceInfoUpdateGateway deviceInfoUpdateGateway;
    @Mock
    private FotaMetrics fotaMetrics;
    @Mock
    private com.wewins.fota.application.load.DynamicIntervalService dynamicIntervalService;

    private UpgradeCheckService upgradeCheckService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        upgradeCheckService = new UpgradeCheckService(
                deviceRepository,
                deviceCacheRepository,
                upgradePolicyRepository,
                deviceRateLimiter,
                bitmapRepository,
                policyMatcher,
                productRepository,
                firmwareVersionLookupService,
                grayReleaseService,
                upgradeResponseBuilder,
                requestValidator,
                checkLogGateway,
                checkLogBuilder,
                deviceInfoUpdateGateway,
                firmwareVersionRepository,
                dynamicIntervalService,
                fotaMetrics
        );
        objectMapper = new ObjectMapper();
        when(deviceCacheRepository.get(anyString())).thenReturn(CacheLookupResult.miss());
    }

    @Nested
    @DisplayName("灰度检查集成测试")
    class GrayIntegrationTests {

        @Test
        @DisplayName("设备命中灰度应该匹配策略")
        void testGrayHit_ShouldMatchPolicy() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            Product product = createProduct(1L, "TEST-MODEL-001");
            UpgradePolicy policy = createPolicy(1L, 50); // 50% 灰度

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString())).thenReturn(Optional.of(product));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(firmwareVersionLookupService.findMatchedFirmwareVersion(any()))
                    .thenReturn(Optional.of(createFirmware(1L)));
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(1L, false))
                    .thenReturn(List.of(policy));
            when(grayReleaseService.hitsGrayBucket(imei, 50)).thenReturn(true); // 命中灰度
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any())).thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any())).thenReturn(true);

            CheckResult expectedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .requestId("test-request-id")
                    .build();
            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), any()))
                    .thenReturn(expectedResult);

            // Act
            UpgradeCheckReqDTO request = createRequest(imei, "TEST-MODEL-001");
            CheckResult result = upgradeCheckService.checkUpgrade(request);

            // Assert
            assertTrue(result.getHasUpdate(), "应该有更新");
            assertEquals(UpgradeDecision.UPDATE, result.getDecision());

            // 业务流程和指标上报都会读取灰度命中情况
            verify(grayReleaseService, atLeastOnce()).hitsGrayBucket(imei, 50);
        }

        @Test
        @DisplayName("设备未命中灰度不应该匹配策略")
        void testGrayMiss_ShouldNotMatchPolicy() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            Product product = createProduct(1L, "TEST-MODEL-001");
            UpgradePolicy policy = createPolicy(1L, 10); // 10% 灰度

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString())).thenReturn(Optional.of(product));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(firmwareVersionLookupService.findMatchedFirmwareVersion(any()))
                    .thenReturn(Optional.of(createFirmware(1L)));
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(1L, false))
                    .thenReturn(List.of(policy));
            when(grayReleaseService.hitsGrayBucket(imei, 10)).thenReturn(false); // 未命中灰度
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any())).thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any())).thenReturn(true);

            // Act
            UpgradeCheckReqDTO request = createRequest(imei, "TEST-MODEL-001");
            CheckResult result = upgradeCheckService.checkUpgrade(request);

            // Assert
            assertFalse(result.getHasUpdate(), "不应该有更新");
            assertEquals(UpgradeDecision.NO_UPDATE, result.getDecision());

            verify(grayReleaseService, atLeastOnce()).hitsGrayBucket(imei, 10);
        }

        @Test
        @DisplayName("灰度比例为 null 时应该全量匹配")
        void testNullGrayRate_ShouldMatchAll() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            Product product = createProduct(1L, "TEST-MODEL-001");
            UpgradePolicy policy = createPolicy(1L, null); // null 灰度比例

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString())).thenReturn(Optional.of(product));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(firmwareVersionLookupService.findMatchedFirmwareVersion(any()))
                    .thenReturn(Optional.of(createFirmware(1L)));
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(1L, false))
                    .thenReturn(List.of(policy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any())).thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any())).thenReturn(true);

            CheckResult expectedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .requestId("test-request-id")
                    .build();
            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), any()))
                    .thenReturn(expectedResult);

            // Act
            UpgradeCheckReqDTO request = createRequest(imei, "TEST-MODEL-001");
            CheckResult result = upgradeCheckService.checkUpgrade(request);

            // Assert
            assertTrue(result.getHasUpdate(), "应该有更新");

            // null 比例不会触发灰度命中计算
            verify(grayReleaseService, never()).hitsGrayBucket(anyString(), anyInt());
        }

        @Test
        @DisplayName("灰度比例为 100 时应该全量匹配")
        void testFullGrayRate_ShouldMatchAll() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            Product product = createProduct(1L, "TEST-MODEL-001");
            UpgradePolicy policy = createPolicy(1L, 100); // 100% 灰度

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString())).thenReturn(Optional.of(product));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(firmwareVersionLookupService.findMatchedFirmwareVersion(any()))
                    .thenReturn(Optional.of(createFirmware(1L)));
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(1L, false))
                    .thenReturn(List.of(policy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any())).thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any())).thenReturn(true);

            CheckResult expectedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .requestId("test-request-id")
                    .build();
            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), any()))
                    .thenReturn(expectedResult);

            // Act
            UpgradeCheckReqDTO request = createRequest(imei, "TEST-MODEL-001");
            CheckResult result = upgradeCheckService.checkUpgrade(request);

            // Assert
            assertTrue(result.getHasUpdate(), "应该有更新");
            // 100% 灰度在匹配阶段跳过，但指标上报仍会读取一次
            verify(grayReleaseService, atLeastOnce()).hitsGrayBucket(imei, 100);
        }

        @Test
        @DisplayName("灰度比例为 0 时不应该匹配任何设备")
        void testZeroGrayRate_ShouldNotMatch() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            Product product = createProduct(1L, "TEST-MODEL-001");
            UpgradePolicy policy = createPolicy(1L, 0); // 0% 灰度

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString())).thenReturn(Optional.of(product));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(firmwareVersionLookupService.findMatchedFirmwareVersion(any()))
                    .thenReturn(Optional.of(createFirmware(1L)));
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(1L, false))
                    .thenReturn(List.of(policy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any())).thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(imei, 0)).thenReturn(false); // 0% 应该返回 false

            // Act
            UpgradeCheckReqDTO request = createRequest(imei, "TEST-MODEL-001");
            CheckResult result = upgradeCheckService.checkUpgrade(request);

            // Assert
            assertFalse(result.getHasUpdate(), "不应该有更新");
            verify(grayReleaseService).hitsGrayBucket(imei, 0);
        }
    }

    /**
     * 创建设备实体
     */
    private Device createDevice(String imei, Long productId, Long versionId, Map<String, String> tags) {
        DeviceVersionParts versionParts = DeviceVersionParts.builder().parts(
                Map.of(
                        "main", DeviceVersionPart.builder()
                                .versionId(versionId)
                                .build()
                )
        ).build();
        Device device = Device.builder()
                .imei(imei)
                .productId(productId)
                .versionParts(versionParts)
                .status("ACTIVE")
                .tags(tags)
                .build();
        device.setId(1L);
        return device;
    }

    /**
     * 创建产品实体
     */
    private Product createProduct(Long id, String model) {
        Product product = Product.builder()
                .name("测试产品")
                .model(model)
                .manufacturer("测试制造商")
                .build();
        product.setId(id);
        return product;
    }

    private FirmwareVersion createFirmware(Long id) {
        FirmwareVersion firmware = FirmwareVersion.builder()
                .productId(1L)
                .version("v1.0.0")
                .internalVersion("BUILD_01")
                .meta(Map.of("part", "main"))
                .build();
        firmware.setId(id);
        return firmware;
    }

    /**
     * 创建升级策略
     */
    private UpgradePolicy createPolicy(Long productId, Integer grayRate) {
        UpgradePolicy policy = UpgradePolicy.builder()
                .productId(productId)
                .name("测试策略")
                .targetVersionId(2L)
                .sourceVersions(Set.of(1L))
                .priority(10)
                .grayRate(grayRate)
                .status(PolicyStatus.ACTIVE)
                .build();
        policy.setId(1L);
        return policy;
    }

    /**
     * 创建请求 DTO
     */
    private UpgradeCheckReqDTO createRequest(String imei, String productModel) {
        UpgradeCheckReqDTO request = new UpgradeCheckReqDTO();
        request.setImei(imei);
        request.setProduct(productModel);
        request.setVersion("v1.0.0");
        request.setAuto(1);
        request.setLang("en");
        return request;
    }
}
