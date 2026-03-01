package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.storage.core.S3StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S3PresignedUrlServiceImpl 单元测试
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@DisplayName("S3PresignedUrlServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class S3PresignedUrlServiceImplTest {

    private static final String TEST_PRESIGNED_URL = "https://bucket.s3.amazonaws.com/fota/fw/123/test-firmware.zip?X-Amz-Signature=abc123&pid=100&did=1000";

    @Mock
    private S3StorageClient s3StorageClient;

    private S3PresignedUrlServiceImpl signedUrlService;
    private FirmwareDownloadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FirmwareDownloadProperties();
        properties.getS3Presigned().setDefaultExpireSeconds(3600);

        when(s3StorageClient.getDownloadUrl(any(String.class), any(Duration.class), any(Map.class)))
                .thenReturn(TEST_PRESIGNED_URL);

        signedUrlService = new S3PresignedUrlServiceImpl(s3StorageClient, properties);
    }

    @Nested
    @DisplayName("generateSignedUrl 方法测试")
    class GenerateSignedUrlTests {

        @Test
        @DisplayName("生成预签名 URL - 基本场景")
        void generateSignedUrl_shouldReturnPresignedUrl_withTrackingParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            Long deviceId = 1000L;

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId);

            // Then
            assertThat(url).isNotEmpty();
            assertThat(url).isEqualTo(TEST_PRESIGNED_URL);

            // 验证 S3 客户端调用，确认传入了正确的溯源参数
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), any(Duration.class), paramsCaptor.capture());

            Map<String, String> params = paramsCaptor.getValue();
            assertThat(params).containsEntry("pid", "100");
            assertThat(params).containsEntry("did", "1000");
        }

        @Test
        @DisplayName("生成预签名 URL - 自定义过期时间")
        void generateSignedUrl_shouldUseCustomExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            Long deviceId = 1000L;
            int customExpireSeconds = 7200;

            // When
            signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId, customExpireSeconds);

            // Then
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), eq(Duration.ofSeconds(customExpireSeconds)), any(Map.class));
        }

        @Test
        @DisplayName("生成预签名 URL - 空固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsNull() {
            // Given
            String firmwarePath = null;
            Long policyId = 100L;
            Long deviceId = 1000L;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成预签名 URL - 空 policyId 抛出异常")
        void generateSignedUrl_shouldThrowException_whenPolicyIdIsNull() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = null;
            Long deviceId = 1000L;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("policyId 必须为正数");
        }

        @Test
        @DisplayName("生成预签名 URL - 空 deviceId 抛出异常")
        void generateSignedUrl_shouldThrowException_whenDeviceIdIsNull() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            Long deviceId = null;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deviceId 必须为正数");
        }

        @Test
        @DisplayName("生成预签名 URL - 过期时间超出范围抛出异常")
        void generateSignedUrl_shouldThrowException_whenExpireTimeTooLong() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            Long deviceId = 1000L;
            int expireSeconds = 8 * 24 * 3600; // 8 天，超过 7 天限制

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId, expireSeconds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expireSeconds 必须在 1-604800 秒之间");
        }
    }

    @Nested
    @DisplayName("verifySignature 方法测试")
    class VerifySignatureTests {

        @Test
        @DisplayName("验证签名 - 未过期返回 true")
        void verifySignature_shouldReturnTrue_whenNotExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            Long deviceId = 1000L;
            long expireTime = System.currentTimeMillis() / 1000 + 3600;
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, deviceId, expireTime, signature);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - 已过期返回 false")
        void verifySignature_shouldReturnFalse_whenExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            Long deviceId = 1000L;
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 1 小时前
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, deviceId, expireTime, signature);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("溯源参数测试")
    class TrackingParamsTests {

        @Test
        @DisplayName("溯源参数正确传递给 S3 客户端")
        void trackingParams_shouldBePassedToS3Client() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 200L;
            Long deviceId = 2000L;

            // When
            signedUrlService.generateSignedUrl(firmwarePath, policyId, deviceId);

            // Then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), any(Duration.class), paramsCaptor.capture());

            Map<String, String> params = paramsCaptor.getValue();
            assertThat(params).containsEntry("pid", "200");
            assertThat(params).containsEntry("did", "2000");
            assertThat(params).hasSize(2);
        }
    }
}
