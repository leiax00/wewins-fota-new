package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.enums.PolicyStatus;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    private UpgradeCheckService upgradeCheckService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        upgradeCheckService = new UpgradeCheckService(
                deviceRepository,
                deviceCacheRepository,
                dataIntegrityService,
                upgradePolicyRepository,
                deviceRateLimiter,
                bitmapRepository,
                policyMatcher,
                productRepository,
                firmwareVersionRepository,
                firmwareVersionLookupService,
                grayReleaseService,
                upgradeResponseBuilder
        );
        objectMapper = new ObjectMapper();
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
            UpgradePolicy policy = createPolicy(1L, 50); // 50% 灰度

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(new RateLimitDecision(true, null, 0));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(1L))
                    .thenReturn(List.of(policy));
            when(dataIntegrityService.isProductActive(any())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(imei, 50)).thenReturn(true); // 命中灰度
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertTrue(result.getHasUpdate(), "应该有更新");
            assertEquals("UPDATE", result.getDecision());

            // 验证灰度检查被调用
            verify(grayReleaseService).hitsGrayBucket(imei, 50);
        }

        @Test
        @DisplayName("设备未命中灰度不应该匹配策略")
        void testGrayMiss_ShouldNotMatchPolicy() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            UpgradePolicy policy = createPolicy(1L, 10); // 10% 灰度

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(new RateLimitDecision(true, null, 0));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(1L))
                    .thenReturn(List.of(policy));
            when(dataIntegrityService.isProductActive(any())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(imei, 10)).thenReturn(false); // 未命中灰度
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertFalse(result.getHasUpdate(), "不应该有更新");
            assertEquals("NO_UPDATE", result.getDecision());

            // 验证灰度检查被调用
            verify(grayReleaseService).hitsGrayBucket(imei, 10);
        }

        @Test
        @DisplayName("灰度比例为 null 时应该全量匹配")
        void testNullGrayRate_ShouldMatchAll() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            UpgradePolicy policy = createPolicy(1L, null); // null 灰度比例

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(new RateLimitDecision(true, null, 0));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(1L))
                    .thenReturn(List.of(policy));
            when(dataIntegrityService.isProductActive(any())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(any())).thenReturn(true);
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertTrue(result.getHasUpdate(), "应该有更新");

            // 验证灰度检查未被调用（null 比例直接返回 true）
            verify(grayReleaseService, never()).hitsGrayBucket(anyString(), anyInt());
        }

        @Test
        @DisplayName("灰度比例为 100 时应该全量匹配")
        void testFullGrayRate_ShouldMatchAll() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            UpgradePolicy policy = createPolicy(1L, 100); // 100% 灰度

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(new RateLimitDecision(true, null, 0));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(1L))
                    .thenReturn(List.of(policy));
            when(dataIntegrityService.isProductActive(any())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(imei, 100)).thenReturn(true); // 100% 应该返回 true
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertTrue(result.getHasUpdate(), "应该有更新");
            verify(grayReleaseService).hitsGrayBucket(imei, 100);
        }

        @Test
        @DisplayName("灰度比例为 0 时不应该匹配任何设备")
        void testZeroGrayRate_ShouldNotMatch() {
            // Arrange
            String imei = "354972069009027";
            Device device = createDevice(imei, 1L, 1L, null);
            UpgradePolicy policy = createPolicy(1L, 0); // 0% 灰度

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(new RateLimitDecision(true, null, 0));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(1L))
                    .thenReturn(List.of(policy));
            when(dataIntegrityService.isProductActive(any())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(imei, 0)).thenReturn(false); // 0% 应该返回 false
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertFalse(result.getHasUpdate(), "不应该有更新");
            verify(grayReleaseService).hitsGrayBucket(imei, 0);
        }
    }

    /**
     * 创建设备实体
     */
    private Device createDevice(String imei, Long productId, Long versionId, JsonNode tags) {
        return Device.builder()
                .id(1L)
                .imei(imei)
                .productId(productId)
                .currentVersionId(versionId)
                .status("ACTIVE")
                .tags(tags)
                .build();
    }

    /**
     * 创建升级策略
     */
    private UpgradePolicy createPolicy(Long productId, Integer grayRate) {
        ObjectNode sourceVersions = objectMapper.createObjectNode().put("dummy", "value");
        return UpgradePolicy.builder()
                .id(1L)
                .productId(productId)
                .name("测试策略")
                .targetVersionId(2L)
                .sourceVersions(sourceVersions)
                .priority(10)
                .grayRate(grayRate)
                .status(PolicyStatus.ACTIVE)
                .build();
    }
}
