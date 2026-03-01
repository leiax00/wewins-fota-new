package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.enums.PolicyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * UpgradeResponseBuilder 单元测试
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpgradeResponseBuilder 单元测试")
class UpgradeResponseBuilderTest {

    @Mock
    private SignedUrlService signedUrlService;

    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;

    @InjectMocks
    private UpgradeResponseBuilder upgradeResponseBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Device testDevice;
    private UpgradePolicy testPolicy;
    private FirmwareVersion testFirmware;

    @BeforeEach
    void setUp() {
        // 创建测试设备
        testDevice = Device.builder()
                .id(1000L)
                .imei("354972069009027")
                .productId(1L)
                .currentVersionId(10L)
                .build();

        // 创建测试策略
        testPolicy = UpgradePolicy.builder()
                .id(100L)
                .productId(1L)
                .name("测试策略")
                .targetVersionId(20L)
                .priority(100)
                .status(PolicyStatus.ACTIVE)
                .build();

        // 创建测试固件版本
        testFirmware = FirmwareVersion.builder()
                .id(20L)
                .productId(1L)
                .version("v2.0.0")
                .internalVersion("BUILD_02")
                .fileUrl("fota/fw/1/test-firmware.zip")
                .fileName("test-firmware.zip")
                .fileSize(20_000_000L)  // 20MB
                .md5("abc123def456")
                .sha256("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .packageStatus("READY")
                .createdAt(LocalDateTime.of(2026, 2, 1, 10, 0, 0))
                .packageUploadedAt(LocalDateTime.of(2026, 2, 1, 10, 30, 0))
                .build();
    }

    @Nested
    @DisplayName("buildResponse 方法测试")
    class BuildResponseTests {

        @Test
        @DisplayName("构建响应 - 基本场景")
        void buildResponse_shouldReturnValidResponse() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/fota/fw/1/test-firmware.zip?pid=100&did=1000&expire=1709222400&sig=abc123");

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isTrue();
            assertThat(result.getDecision()).isEqualTo("UPDATE");
            assertThat(result.getTargetVersionId()).isEqualTo(20L);
            assertThat(result.getTargetVersion()).isEqualTo("v2.0.0");
            assertThat(result.getPolicyId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("构建响应 - 包含签名 URL")
        void buildResponse_shouldIncludeSignedUrl() {
            // Given
            String expectedUrl = "https://cdn.example.com/fota/fw/1/test-firmware.zip?pid=100&did=1000&expire=1709222400&sig=abc123";
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong())).thenReturn(expectedUrl);

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("downloadUrl")).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("构建响应 - 文件大小格式化")
        void buildResponse_shouldFormatFileSize() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("fileSize")).isEqualTo(20_000_000L);
            assertThat(result.getExt().get("fileSizeText")).isEqualTo("19.1MB");
        }

        @Test
        @DisplayName("构建响应 - SHA-256 校验和优先")
        void buildResponse_shouldPreferSha256Checksum() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("checksum")).isEqualTo(testFirmware.getSha256());
            assertThat(result.getExt().get("checksumType")).isEqualTo("sha256");
        }

        @Test
        @DisplayName("构建响应 - 自动模式检查间隔更长")
        void buildResponse_shouldUseLongerInterval_forAutoMode() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult autoResult = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", true);
            UpgradeCheckService.CheckResult manualResult = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(autoResult.getResponseCheckInterval()).isEqualTo(86400);  // 24 小时
            assertThat(manualResult.getResponseCheckInterval()).isEqualTo(3600);  // 1 小时
        }

        @Test
        @DisplayName("构建响应 - 固件不存在返回错误")
        void buildResponse_shouldReturnError_whenFirmwareNotFound() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.empty());

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("ERROR");
            assertThat(result.getErrorMessage()).isEqualTo("目标固件版本不存在");
        }

        @Test
        @DisplayName("构建响应 - 固件包未就绪返回错误")
        void buildResponse_shouldReturnError_whenFirmwareNotReady() {
            // Given
            testFirmware.setPackageStatus("UPLOADED");
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("ERROR");
            assertThat(result.getErrorMessage()).isEqualTo("固件包未准备好");
        }
    }

    @Nested
    @DisplayName("多语言 release_note 测试")
    class I18nReleaseNoteTests {

        @Test
        @DisplayName("选择中文 release_note")
        void buildResponse_shouldSelectChineseReleaseNote() {
            // Given - 设置多语言元数据
            ObjectNode meta = objectMapper.createObjectNode();
            ObjectNode i18n = meta.putObject("i18n");
            ObjectNode zhNode = i18n.putObject("zh");
            zhNode.put("description", "修复蓝牙断连问题");
            ObjectNode enNode = i18n.putObject("en");
            enNode.put("description", "Fix Bluetooth disconnection");
            testFirmware.setMeta(meta);

            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "zh", false);

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("releaseNote")).isEqualTo("修复蓝牙断连问题");
        }

        @Test
        @DisplayName("降级到英文 release_note")
        void buildResponse_shouldFallbackToEnglish_whenRequestedLanguageNotFound() {
            // Given
            ObjectNode meta = objectMapper.createObjectNode();
            ObjectNode i18n = meta.putObject("i18n");
            ObjectNode enNode = i18n.putObject("en");
            enNode.put("description", "Bug fixes and improvements");
            testFirmware.setMeta(meta);

            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "fr", false);

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("releaseNote")).isEqualTo("Bug fixes and improvements");
        }

        @Test
        @DisplayName("优先使用 changelog 而非 description")
        void buildResponse_shouldPreferChangelogOverDescription() {
            // Given
            ObjectNode meta = objectMapper.createObjectNode();
            ObjectNode i18n = meta.putObject("i18n");
            ObjectNode enNode = i18n.putObject("en");
            enNode.put("description", "Simple description");
            enNode.put("changelog", "1. Fix bug A\n2. Fix bug B");
            testFirmware.setMeta(meta);

            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(any(), anyLong(), anyLong()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, "en", false);

            // Then
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("releaseNote")).isEqualTo("1. Fix bug A\n2. Fix bug B");
        }
    }

    @Nested
    @DisplayName("静态工厂方法测试")
    class StaticFactoryMethodTests {

        @Test
        @DisplayName("创建无更新响应")
        void buildNoUpdateResponse_shouldReturnNoUpdate() {
            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildNoUpdateResponse();

            // Then
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("NO_UPDATE");
            assertThat(result.getResponseCheckInterval()).isEqualTo(86400);
        }

        @Test
        @DisplayName("创建设备不存在响应")
        void buildNotFoundResponse_shouldReturnNotFound() {
            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildNotFoundResponse("设备未注册");

            // Then
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
            assertThat(result.getErrorMessage()).isEqualTo("设备未注册");
        }

        @Test
        @DisplayName("创建限流响应")
        void buildRateLimitedResponse_shouldReturnRateLimited() {
            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildRateLimitedResponse("请求过于频繁", 300);

            // Then
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("RATE_LIMITED");
            assertThat(result.getErrorMessage()).isEqualTo("请求过于频繁");
            assertThat(result.getResponseCheckInterval()).isEqualTo(300);
            assertThat(result.getDownloadDelay()).isEqualTo(300);
        }

        @Test
        @DisplayName("创建错误响应")
        void buildErrorResponse_shouldReturnError() {
            // When
            UpgradeCheckService.CheckResult result = upgradeResponseBuilder.buildErrorResponse("INVALID_PARAM", "参数错误");

            // Then
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("ERROR");
            assertThat(result.getErrorMessage()).isEqualTo("参数错误");
            assertThat(result.getExt()).isNotNull();
            assertThat(result.getExt().get("errorCode")).isEqualTo("INVALID_PARAM");
        }
    }
}
