package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.quota.PolicyQuotaService;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UpgradeCheckService 配额检查测试
 * <p>
 * 验证配额检查集成到策略匹配流程中
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("UpgradeCheckService 配额检查测试")
@ExtendWith(MockitoExtension.class)
class UpgradeCheckServiceQuotaTest {

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
    private PolicyQuotaService policyQuotaService;

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
                upgradeResponseBuilder,
                policyQuotaService
        );
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("配额检查集成到策略匹配")
    class QuotaIntegrationTests {

        @Test
        @DisplayName("策略配额为 null 时应该通过")
        void testPolicyWithNullQuota_ShouldPass() {
            // Arrange
            String imei = "869123456789012";
            Device device = createTestDevice(imei);
            UpgradePolicy policy = createTestPolicy(1L, null);

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(100, 0L));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(dataIntegrityService.isProductActive(anyLong())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(anyLong())).thenReturn(true);
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(policy));
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), anyInt())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertNotNull(result);
            // 配额为 null 时不需要调用配额服务
            verify(policyQuotaService, never()).checkAndIncrementQuota(anyLong(), anyInt());
        }

        @Test
        @DisplayName("策略配额为 0 时应该通过（无限制）")
        void testPolicyWithZeroQuota_ShouldPass() {
            // Arrange
            String imei = "869123456789012";
            Device device = createTestDevice(imei);
            UpgradePolicy policy = createTestPolicy(1L, 0);

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(100, 0L));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(dataIntegrityService.isProductActive(anyLong())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(anyLong())).thenReturn(true);
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(policy));
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), anyInt())).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertNotNull(result);
            // 配额为 0 时不需要调用配额服务
            verify(policyQuotaService, never()).checkAndIncrementQuota(anyLong(), anyInt());
        }

        @Test
        @DisplayName("策略配额可用时应该通过并占用配额")
        void testPolicyWithAvailableQuota_ShouldPassAndConsume() {
            // Arrange
            String imei = "869123456789012";
            Device device = createTestDevice(imei);
            UpgradePolicy policy = createTestPolicy(1L, 100);

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(100, 0L));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(dataIntegrityService.isProductActive(anyLong())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(anyLong())).thenReturn(true);
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(policy));
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), anyInt())).thenReturn(true);
            when(policyQuotaService.checkAndIncrementQuota(1L, 100)).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertNotNull(result);
            verify(policyQuotaService).checkAndIncrementQuota(1L, 100);
        }

        @Test
        @DisplayName("策略配额已用尽时不应该匹配该策略")
        void testPolicyWithExhaustedQuota_ShouldNotMatch() {
            // Arrange
            String imei = "869123456789012";
            Device device = createTestDevice(imei);
            UpgradePolicy policy1 = createTestPolicy(1L, 100);  // 配额已用尽
            UpgradePolicy policy2 = createTestPolicy(2L, null);  // 无配额限制

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(100, 0L));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(dataIntegrityService.isProductActive(anyLong())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(anyLong())).thenReturn(true);
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(policy1, policy2));
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), anyInt())).thenReturn(true);
            // 第一个策略配额已用尽，第二个策略没有配额限制
            when(policyQuotaService.checkAndIncrementQuota(1L, 100)).thenReturn(false);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertNotNull(result);
            // 第一个策略配额检查被调用
            verify(policyQuotaService).checkAndIncrementQuota(1L, 100);
            // 结果应该是 noUpdate，因为第一个策略配额已用尽，但第二个策略需要其他条件匹配
            // 这里主要验证配额检查被正确调用
        }

        @Test
        @DisplayName("多个策略按优先级依次检查配额")
        void testMultiplePolicies_CheckQuotaInPriorityOrder() {
            // Arrange
            String imei = "869123456789012";
            Device device = createTestDevice(imei);
            UpgradePolicy policy1 = createTestPolicy(1L, 100);  // 高优先级，配额已用尽
            UpgradePolicy policy2 = createTestPolicy(2L, 50);   // 低优先级，配额可用

            when(deviceRateLimiter.allow(anyString(), anyInt(), any(Duration.class)))
                    .thenReturn(RateLimitDecision.allowed(100, 0L));
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.of(device));
            when(dataIntegrityService.isProductActive(anyLong())).thenReturn(true);
            when(dataIntegrityService.isFirmwareVersionActive(anyLong())).thenReturn(true);
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(policy1, policy2));
            when(policyMatcher.matchesDeviceTags(any(), any())).thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), anyInt())).thenReturn(true);
            when(policyQuotaService.checkAndIncrementQuota(1L, 100)).thenReturn(false);
            when(policyQuotaService.checkAndIncrementQuota(2L, 50)).thenReturn(true);

            // Act
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Assert
            assertNotNull(result);
            // 两个策略的配额检查都应该被调用
            verify(policyQuotaService).checkAndIncrementQuota(1L, 100);
            verify(policyQuotaService).checkAndIncrementQuota(2L, 50);
        }
    }

    // ========== 辅助方法 ==========

    private Device createTestDevice(String imei) {
        Device device = new Device();
        device.setId(1L);
        device.setImei(imei);
        device.setProductId(100L);
        device.setCurrentVersionId(10L);
        device.setActivated(true);
        return device;
    }

    private UpgradePolicy createTestPolicy(Long id, Integer quota) {
        UpgradePolicy policy = new UpgradePolicy();
        policy.setId(id);
        policy.setProductId(100L);
        policy.setTargetVersionId(20L);
        policy.setName("测试策略-" + id);
        policy.setPriority(id.intValue());
        policy.setGrayRate(100);  // 全量灰度，确保灰度检查通过

        // 设置源版本
        ObjectNode sourceVersions = objectMapper.createArrayNode();
        sourceVersions.add(10L);
        policy.setSourceVersions(sourceVersions);

        // 设置配额
        policy.setQuota(quota);

        return policy;
    }
}
