package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.quota.PolicyQuotaService;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * UpgradeCheckService 集成测试
 * <p>
 * 验证完整的升级检查流程，包括响应构建和签名 URL 生成
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpgradeCheckService 集成测试")
class UpgradeCheckServiceIntegrationTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCacheRepository deviceCacheService;
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
    private SignedUrlService signedUrlService;
    @Mock
    private PolicyQuotaService policyQuotaService;
    @Mock
    private UpgradeRequestValidator requestValidator;

    @InjectMocks
    private UpgradeCheckService upgradeCheckService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Device testDevice;
    private UpgradePolicy testPolicy;
    private FirmwareVersion targetFirmware;
    private FirmwareVersion currentFirmware;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 创建测试产品
        testProduct = Product.builder()
                .id(1L)
                .name("测试产品")
                .model("TEST-MODEL-001")
                .manufacturer("测试制造商")
                .build();

        // 创建当前固件版本
        currentFirmware = FirmwareVersion.builder()
                .id(10L)
                .productId(1L)
                .version("v1.0.0")
                .internalVersion("BUILD_01")
                .packageStatus("READY")
                .build();

        // 创建目标固件版本
        targetFirmware = FirmwareVersion.builder()
                .id(20L)
                .productId(1L)
                .version("v2.0.0")
                .internalVersion("BUILD_02")
                .fileUrl("fota/fw/1/test-firmware.zip")
                .fileName("test-firmware.zip")
                .fileSize(20_000_000L)
                .md5("abc123def456")
                .sha256("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .packageStatus("READY")
                .createdAt(LocalDateTime.of(2026, 2, 1, 10, 0, 0))
                .build();

        // 创建测试设备
        testDevice = Device.builder()
                .id(1000L)
                .imei("354972069009027")
                .productId(1L)
                .currentVersionId(10L)
                .status("ACTIVE")
                .build();

        // 创建测试策略
        testPolicy = UpgradePolicy.builder()
                .id(100L)
                .productId(1L)
                .name("测试升级策略")
                .targetVersionId(20L)
                .priority(100)
                .grayRate(100)  // 全量灰度
                .status("ACTIVE")
                .build();
    }

    @Nested
    @DisplayName("端到端升级检查流程测试")
    class EndToEndUpgradeCheckTests {

        @Test
        @DisplayName("完整流程 - 设备有可用更新")
        void checkUpgrade_shouldReturnUpdateResult_whenPolicyMatched() {
            // Given - 模拟所有依赖
            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(testPolicy));
            when(firmwareVersionRepository.findById(20L))
                    .thenReturn(Optional.of(targetFirmware));
            when(signedUrlService.generateSignedUrl(anyString(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/fota/fw/1/test-firmware.zip?policy=100&device=1000&expire=1709222400&sig=abc123");

            // When
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade("354972069009027");

            // Then - 验证响应
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isTrue();
            assertThat(result.getDecision()).isEqualTo("UPDATE");
            assertThat(result.getTargetVersionId()).isEqualTo(20L);
            assertThat(result.getPolicyId()).isEqualTo(100L);

            // 验证扩展数据
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("downloadUrl"))
                    .isEqualTo("https://cdn.example.com/fota/fw/1/test-firmware.zip?policy=100&device=1000&expire=1709222400&sig=abc123");
            assertThat(result.getExt().get("fileSize")).isEqualTo(20_000_000L);
            assertThat(result.getExt().get("fileSizeText")).isEqualTo("19.1MB");
            assertThat(result.getExt().get("checksum")).isEqualTo(targetFirmware.getSha256());
            assertThat(result.getExt().get("checksumType")).isEqualTo("sha256");
        }

        @Test
        @DisplayName("完整流程 - 设备无可用更新")
        void checkUpgrade_shouldReturnNoUpdate_whenNoPolicyMatched() {
            // Given
            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of());  // 无匹配策略

            // When
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade("354972069009027");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("NO_UPDATE");
        }

        @Test
        @DisplayName("完整流程 - 设备不存在")
        void checkUpgrade_shouldReturnNotFound_whenDeviceNotFound() {
            // Given
            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.empty());

            // When
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade("354972069009027");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("签名 URL 集成测试")
    class SignedUrlIntegrationTests {

        @Test
        @DisplayName("签名 URL 正确集成到响应中")
        void checkUpgrade_shouldIncludeSignedUrl_inResponse() {
            // Given
            String expectedSignedUrl = "https://cdn.example.com/fota/fw/1/test-firmware.zip"
                    + "?policy=100&device=1000&expire=1709222400"
                    + "&sig=3a7b8f9c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8";

            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(testPolicy));
            when(firmwareVersionRepository.findById(20L))
                    .thenReturn(Optional.of(targetFirmware));
            when(signedUrlService.generateSignedUrl(
                    eq("fota/fw/1/test-firmware.zip"),
                    eq(100L),
                    eq(1000L)
            )).thenReturn(expectedSignedUrl);

            // When
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade("354972069009027");

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("downloadUrl")).isEqualTo(expectedSignedUrl);
        }

        @Test
        @DisplayName("签名 URL 服务失败时响应不包含 downloadUrl")
        void checkUpgrade_shouldHandleSignedUrlFailure_gracefully() {
            // Given
            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(testPolicy));
            when(firmwareVersionRepository.findById(20L))
                    .thenReturn(Optional.of(targetFirmware));
            when(signedUrlService.generateSignedUrl(anyString(), anyLong(), anyLong()))
                    .thenThrow(new RuntimeException("签名服务异常"));

            // When
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade("354972069009027");

            // Then - 响应仍然应该成功，只是没有 downloadUrl
            assertThat(result.getHasUpdate()).isTrue();
            assertThat(result.getDecision()).isEqualTo("UPDATE");
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("downloadUrl")).isNull();
        }
    }

    @Nested
    @DisplayName("多语言响应测试")
    class I18nResponseTests {

        @Test
        @DisplayName("返回中文 release_note")
        void checkUpgrade_shouldReturnChineseReleaseNote_whenLangIsZh() {
            // Given - 设置多语言元数据
            ObjectNode meta = objectMapper.createObjectNode();
            ObjectNode i18n = meta.putObject("i18n");
            ObjectNode zhNode = i18n.putObject("zh");
            zhNode.put("changelog", "修复蓝牙断连问题\n优化功耗");
            targetFirmware.setMeta(meta);

            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(firmwareVersionLookupService.lookupVersion(anyString(), anyString(), anyLong()))
                    .thenReturn(Optional.of(currentFirmware));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(testPolicy));
            when(firmwareVersionRepository.findById(20L))
                    .thenReturn(Optional.of(targetFirmware));
            when(signedUrlService.generateSignedUrl(anyString(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult result = upgradeCheckService.checkUpgrade(
                    "TEST-MODEL-001", "354972069009027", "v1.0.0", null, 0, "zh", null
            );

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("releaseNote")).isEqualTo("修复蓝牙断连问题\n优化功耗");
        }
    }

    @Nested
    @DisplayName("控制参数测试")
    class ControlParamsTests {

        @Test
        @DisplayName("自动模式使用更长的检查间隔")
        void checkUpgrade_shouldUseLongerInterval_forAutoMode() {
            // Given
            when(deviceRateLimiter.allow(anyString(), anyInt(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(firmwareVersionLookupService.lookupVersion(anyString(), anyString(), anyLong()))
                    .thenReturn(Optional.of(currentFirmware));
            when(upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(anyLong()))
                    .thenReturn(List.of(testPolicy));
            when(firmwareVersionRepository.findById(20L))
                    .thenReturn(Optional.of(targetFirmware));
            when(signedUrlService.generateSignedUrl(anyString(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When - 自动模式
            UpgradeCheckService.CheckResult autoResult = upgradeCheckService.checkUpgrade(
                    "TEST-MODEL-001", "354972069009027", "v1.0.0", null, 1, "en", null
            );

            // When - 手动模式
            UpgradeCheckService.CheckResult manualResult = upgradeCheckService.checkUpgrade(
                    "TEST-MODEL-001", "354972069009027", "v1.0.0", null, 0, "en", null
            );

            // Then
            assertThat(autoResult.getResponseCheckInterval()).isEqualTo(86400);  // 24 小时
            assertThat(manualResult.getResponseCheckInterval()).isEqualTo(3600);  // 1 小时
        }
    }
}
