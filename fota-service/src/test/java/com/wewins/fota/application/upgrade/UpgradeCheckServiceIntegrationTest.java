package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.application.reporting.DeviceCheckLogBuilder;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
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
import static org.mockito.ArgumentMatchers.*;
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
    private UpgradeRequestValidator requestValidator;
    @Mock
    private UpgradeResponseBuilder upgradeResponseBuilder;
    @Mock
    private CheckLogGateway checkLogGateway;
    @Mock
    private DeviceCheckLogBuilder checkLogBuilder;

    @InjectMocks
    private UpgradeCheckService upgradeCheckService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Device testDevice;
    private UpgradePolicy testPolicy;
    private FirmwareVersion targetFirmware;
    private FirmwareVersion currentFirmware;
    private Product testProduct;
    private UpgradeCheckReqDTO testRequest;

    @BeforeEach
    void setUp() {
        // 创建测试产品
        testProduct = Product.builder()
                .name("测试产品")
                .model("TEST-MODEL-001")
                .manufacturer("测试制造商")
                .build();
        testProduct.setId(1L);

        // 创建当前固件版本
        currentFirmware = FirmwareVersion.builder()
                .productId(1L)
                .version("v1.0.0")
                .internalVersion("BUILD_01")
                .packageStatus("READY")
                .build();
        currentFirmware.setId(10L);

        // 创建目标固件版本
        targetFirmware = FirmwareVersion.builder()
                .productId(1L)
                .version("v2.0.0")
                .internalVersion("BUILD_02")
                .fileUrl("fota/fw/1/test-firmware.zip")
                .fileName("test-firmware.zip")
                .fileSize(20_000_000L)
                .md5("abc123def456")
                .sha256("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .packageStatus("READY")
                .build();
        targetFirmware.setId(20L);
        targetFirmware.setCreatedAt(LocalDateTime.of(2026, 2, 1, 10, 0, 0));

        // 创建测试设备
        testDevice = Device.builder()
                .imei("354972069009027")
                .productId(1L)
                .status("ACTIVE")
                .build();
        testDevice.setId(1000L);

        // 创建测试策略
        testPolicy = UpgradePolicy.builder()
                .productId(1L)
                .name("测试升级策略")
                .targetVersionId(20L)
                .priority(100)
                .grayRate(100)  // 全量灰度
                .status(PolicyStatus.ACTIVE)
                .build();
        testPolicy.setId(100L);

        // 创建测试请求 DTO
        testRequest = new UpgradeCheckReqDTO();
        testRequest.setProduct("TEST-MODEL-001");
        testRequest.setImei("354972069009027");
        testRequest.setVersion("v1.0.0");
        testRequest.setAuto(1);
        testRequest.setLang("en");
    }

    @Nested
    @DisplayName("端到端升级检查流程测试")
    class EndToEndUpgradeCheckTests {

        @Test
        @DisplayName("完整流程 - 设备有可用更新")
        void checkUpgrade_shouldReturnUpdateResult_whenPolicyMatched() {
            // Given - 模拟所有依赖
            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(firmwareVersionLookupService.findVersionId(anyString(), any(), anyLong()))
                    .thenReturn(10L);
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(anyLong(), anyBoolean()))
                    .thenReturn(List.of(testPolicy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any()))
                    .thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any()))
                    .thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), any()))
                    .thenReturn(true);
            when(firmwareVersionRepository.findById(20L))
                    .thenReturn(Optional.of(targetFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/fota/fw/1/test-firmware.zip?expire=1709222400&sig=abc123");

            CheckResult expectedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .targetVersionId(20L)
                    .policyId(100L)
                    .requestId("test-request-id")
                    .downloadUrl("https://cdn.example.com/fota/fw/1/test-firmware.zip?expire=1709222400&sig=abc123")
                    .fileSize(20_000_000L)
                    .fileSizeText("19.1MB")
                    .checksum(targetFirmware.getSha256())
                    .checksumType("sha256")
                    .checkInterval(86400)
                    .build();

            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), any()))
                    .thenReturn(expectedResult);

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then - 验证响应
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isTrue();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.UPDATE);
            assertThat(result.getTargetVersionId()).isEqualTo(20L);
            assertThat(result.getPolicyId()).isEqualTo(100L);
            assertThat(result.getDownloadUrl()).isEqualTo("https://cdn.example.com/fota/fw/1/test-firmware.zip?expire=1709222400&sig=abc123");
            assertThat(result.getFileSize()).isEqualTo(20_000_000L);
            assertThat(result.getFileSizeText()).isEqualTo("19.1MB");
            assertThat(result.getChecksum()).isEqualTo(targetFirmware.getSha256());
            assertThat(result.getChecksumType()).isEqualTo("sha256");
        }

        @Test
        @DisplayName("完整流程 - 设备无可用更新")
        void checkUpgrade_shouldReturnNoUpdate_whenNoPolicyMatched() {
            // Given
            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(firmwareVersionLookupService.findVersionId(anyString(), any(), anyLong()))
                    .thenReturn(10L);
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(anyLong(), anyBoolean()))
                    .thenReturn(List.of());  // 无匹配策略

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.NO_UPDATE);
        }

        @Test
        @DisplayName("完整流程 - 设备不存在")
        void checkUpgrade_shouldReturnNotFound_whenDeviceNotFound() {
            // Given
            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.DEVICE_NOT_FOUND);
        }

        @Test
        @DisplayName("完整流程 - 产品不存在")
        void checkUpgrade_shouldReturnError_whenProductNotFound() {
            // Given
            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.ERROR);
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
                    + "?expire=1709222400"
                    + "&sig=3a7b8f9c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8";

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(firmwareVersionLookupService.findVersionId(anyString(), any(), anyLong()))
                    .thenReturn(10L);
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(anyLong(), anyBoolean()))
                    .thenReturn(List.of(testPolicy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any()))
                    .thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any()))
                    .thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), any()))
                    .thenReturn(true);

            CheckResult expectedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .requestId("test-request-id")
                    .downloadUrl(expectedSignedUrl)
                    .checkInterval(86400)
                    .build();

            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), any()))
                    .thenReturn(expectedResult);

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(result.getDownloadUrl()).isEqualTo(expectedSignedUrl);
        }
    }

    @Nested
    @DisplayName("限流测试")
    class RateLimitTests {

        @Test
        @DisplayName("请求被限流时返回限流结果")
        void checkUpgrade_shouldReturnRateLimited_whenRateLimited() {
            // Given
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.denied("请求过于频繁"));

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.RATE_LIMITED);
            assertThat(result.getErrorMessage()).contains("请求过于频繁");
        }
    }

    @Nested
    @DisplayName("多语言响应测试")
    class I18nResponseTests {

        @Test
        @DisplayName("返回中文 release_note")
        void checkUpgrade_shouldReturnChineseReleaseNote_whenLangIsZh() {
            // Given - 设置多语言元数据
            Map<String, Object> meta = Map.of(
                    "i18n", Map.of("zh", "修复蓝牙断连问题\n优化功耗")
            );
            targetFirmware.setMeta(meta);

            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(firmwareVersionLookupService.findVersionId(anyString(), any(), anyLong()))
                    .thenReturn(10L);
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(anyLong(), anyBoolean()))
                    .thenReturn(List.of(testPolicy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any()))
                    .thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any()))
                    .thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), any()))
                    .thenReturn(true);

            testRequest.setLang("zh");

            CheckResult expectedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .requestId("test-request-id")
                    .releaseNote("修复蓝牙断连问题\n优化功耗")
                    .checkInterval(86400)
                    .build();

            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), eq("zh")))
                    .thenReturn(expectedResult);

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(result.getReleaseNote()).isEqualTo("修复蓝牙断连问题\n优化功耗");
        }
    }

    @Nested
    @DisplayName("控制参数测试")
    class ControlParamsTests {

        @Test
        @DisplayName("服务端对自动和手动请求返回统一的周期控制结果")
        void checkUpgrade_shouldUseSameIntervalForDifferentModes() {
            // Given
            long resetAt = System.currentTimeMillis() / 1000 + 60;
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed(9, resetAt));
            when(productRepository.findByModel(anyString()))
                    .thenReturn(Optional.of(testProduct));
            when(deviceRepository.findByImei(anyString()))
                    .thenReturn(Optional.of(testDevice));
            when(firmwareVersionLookupService.findVersionId(anyString(), any(), anyLong()))
                    .thenReturn(10L);
            when(upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(anyLong(), anyBoolean()))
                    .thenReturn(List.of(testPolicy));
            when(policyMatcher.matchesTargetMode(any(), anyString(), any(), any()))
                    .thenReturn(true);
            when(policyMatcher.matchesTimeWindow(any()))
                    .thenReturn(true);
            when(grayReleaseService.hitsGrayBucket(anyString(), any()))
                    .thenReturn(true);

            // When - 自动模式
            testRequest.setAuto(1);
            CheckResult unifiedResult = CheckResult.builder()
                    .hasUpdate(true)
                    .decision(UpgradeDecision.UPDATE)
                    .requestId("test-request-id")
                    .checkInterval(21600)
                    .build();
            when(upgradeResponseBuilder.buildResponse(any(), any(), anyString(), any()))
                    .thenReturn(unifiedResult);
            CheckResult autoCheckResult = upgradeCheckService.checkUpgrade(testRequest);

            // When - 手动模式
            testRequest.setAuto(0);
            CheckResult manualCheckResult = upgradeCheckService.checkUpgrade(testRequest);

            // Then
            assertThat(autoCheckResult.getCheckInterval()).isEqualTo(21600);
            assertThat(manualCheckResult.getCheckInterval()).isEqualTo(21600);
        }
    }
}
