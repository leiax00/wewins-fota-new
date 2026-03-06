package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    private String testRequestId;

    @BeforeEach
    void setUp() {
        // 生成测试用 request_id
        testRequestId = UUID.randomUUID().toString().replace("-", "");

        // 创建测试设备
        testDevice = Device.builder()
                .imei("354972069009027")
                .productId(1L)
                .currentVersionId(10L)
                .build();
        testDevice.setId(1000L);

        // 创建测试策略
        testPolicy = UpgradePolicy.builder()
                .productId(1L)
                .name("测试策略")
                .targetVersionId(20L)
                .priority(100)
                .status(PolicyStatus.ACTIVE)
                .build();
        testPolicy.setId(100L);

        // 创建测试固件版本
        testFirmware = FirmwareVersion.builder()
                .productId(1L)
                .version("v2.0.0")
                .internalVersion("BUILD_02")
                .fileUrl("fota/fw/1/test-firmware.zip")
                .fileName("test-firmware.zip")
                .fileSize(20_000_000L)  // 20MB
                .md5("abc123def456")
                .sha256("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .packageStatus("READY")
                .packageUploadedAt(LocalDateTime.of(2026, 2, 1, 10, 30, 0))
                .build();
        testFirmware.setId(20L);
        testFirmware.setCreatedAt(LocalDateTime.of(2026, 2, 1, 10, 0, 0));
    }

    @Nested
    @DisplayName("buildResponse 方法测试")
    class BuildResponseTests {

        @Test
        @DisplayName("构建响应 - 基本场景")
        void buildResponse_shouldReturnValidResponse() {
            // Given
            String expectedUrl = "https://cdn.example.com/fota/fw/1/test-firmware.zip?pid=100&rid=" + testRequestId;

            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn(expectedUrl);

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isTrue();
            assertThat(result.getPolicyId()).isEqualTo(100L);
            assertThat(result.getRequestId()).isEqualTo(testRequestId);
            assertThat(result.getDownloadUrl()).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("构建响应 - 包含正确的固件信息")
        void buildResponse_shouldIncludeFirmwareInfo() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(result.getTargetVersion()).isEqualTo("v2.0.0");
            assertThat(result.getFileSize()).isEqualTo(20_000_000L);
            assertThat(result.getFileSizeText()).isEqualTo("19.1MB");
            assertThat(result.getChecksum()).isEqualTo(testFirmware.getSha256());
            assertThat(result.getChecksumType()).isEqualTo("sha256");
        }

        @Test
        @DisplayName("构建响应 - 固件版本不存在返回错误")
        void buildResponse_shouldReturnError_whenFirmwareNotFound() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.empty());

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("目标固件版本不存在");
        }

        @Test
        @DisplayName("构建响应 - 固件包未准备好返回错误")
        void buildResponse_shouldReturnError_whenFirmwareNotReady() {
            // Given
            testFirmware.setPackageStatus("PENDING");
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("固件包未准备好");
        }

        @Test
        @DisplayName("构建响应 - 签名 URL 生成失败时 downloadUrl 为 null")
        void buildResponse_shouldHandleNullDownloadUrl() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenThrow(new RuntimeException("签名服务异常"));

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(result.getHasUpdate()).isTrue();
            assertThat(result.getDownloadUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("多语言 release_note 测试")
    class I18nReleaseNoteTests {

        @Test
        @DisplayName("返回中文 release_note")
        void buildResponse_shouldReturnChineseReleaseNote_whenLangIsZh() {
            // Given - 设置多语言元数据
            ObjectNode meta = objectMapper.createObjectNode();
            ObjectNode i18n = meta.putObject("i18n");
            i18n.put("zh", "修复蓝牙断连问题\n优化功耗");
            testFirmware.setMeta(meta);

            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "zh", false);

            // Then
            assertThat(result.getReleaseNote()).isEqualTo("修复蓝牙断连问题\n优化功耗");
        }

        @Test
        @DisplayName("优先使用 changelog 而非 description")
        void buildResponse_shouldPreferChangelogOverDescription() {
            // Given
            ObjectNode meta = objectMapper.createObjectNode();
            ObjectNode i18n = meta.putObject("i18n");
            i18n.put("en", "1. Fix bug A\n2. Fix bug B");
            testFirmware.setMeta(meta);

            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            var result = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(result.getReleaseNote()).isEqualTo("1. Fix bug A\n2. Fix bug B");
        }
    }

    @Nested
    @DisplayName("控制参数测试")
    class ControlParamsTests {

        @Test
        @DisplayName("自动模式使用更长的检查间隔")
        void buildResponse_shouldUseLongerInterval_forAutoMode() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When - 自动模式
            var autoResult = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", true);

            // Then
            assertThat(autoResult.getCheckInterval()).isEqualTo(86400);  // 24 小时
        }

        @Test
        @DisplayName("手动模式使用默认检查间隔")
        void buildResponse_shouldUseDefaultInterval_forManualMode() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When - 手动模式
            var manualResult = upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            assertThat(manualResult.getCheckInterval()).isEqualTo(3600);  // 1 小时
        }
    }

    @Nested
    @DisplayName("签名 URL 调用验证")
    class SignedUrlCallTests {

        @Test
        @DisplayName("签名 URL 服务被正确调用")
        void buildResponse_shouldCallSignedUrlService_withCorrectParams() {
            // Given
            when(firmwareVersionRepository.findById(20L)).thenReturn(Optional.of(testFirmware));
            when(signedUrlService.generateSignedUrl(anyString()))
                    .thenReturn("https://cdn.example.com/firmware.zip");

            // When
            upgradeResponseBuilder.buildResponse(testDevice, testPolicy, testRequestId, "en", false);

            // Then
            verify(signedUrlService).generateSignedUrl(
                    eq("fota/fw/1/test-firmware.zip")
            );
        }
    }
}
