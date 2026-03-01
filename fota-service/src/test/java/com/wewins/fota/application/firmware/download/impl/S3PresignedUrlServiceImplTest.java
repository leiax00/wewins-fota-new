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
import java.util.UUID;

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

    private static final String TEST_REQUEST_ID = "550e8400e29b41d4a716446655440000";
    private static final String TEST_PRESIGNED_URL = "https://bucket.s3.amazonaws.com/fota/fw/123/test-firmware.zip?X-Amz-Signature=abc123&pid=100&rid=" + TEST_REQUEST_ID;

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
            String requestId = UUID.randomUUID().toString().replace("-", "");

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).isNotEmpty();
            assertThat(url).isEqualTo(TEST_PRESIGNED_URL);

            // 验证 S3 客户端调用，确认传入了正确的溯源参数
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), any(Duration.class), paramsCaptor.capture());

            Map<String, String> params = paramsCaptor.getValue();
            assertThat(params).containsEntry("pid", "100");
            assertThat(params).containsEntry("rid", requestId);
        }

        @Test
        @DisplayName("生成预签名 URL - 自定义过期时间")
        void generateSignedUrl_shouldUseCustomExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString().replace("-", "");
            int customExpireSeconds = 7200;

            // When
            signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, customExpireSeconds);

            // Then
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), eq(Duration.ofSeconds(customExpireSeconds)), any(Map.class));
        }

        @Test
        @DisplayName("生成预签名 URL - 空 requestId 抛出异常")
        void generateSignedUrl_shouldThrowException_whenRequestIdIsNull() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = null;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId 不能为空");
        }

        @Test
        @DisplayName("生成预签名 URL - 过期时间超出范围抛出异常")
        void generateSignedUrl_shouldThrowException_whenExpireTimeTooLong() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString().replace("-", "");
            int expireSeconds = 8 * 24 * 3600; // 8 天，超过 7 天限制

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, expireSeconds))
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
            String requestId = UUID.randomUUID().toString().replace("-", "");
            long expireTime = System.currentTimeMillis() / 1000 + 3600;
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, requestId, expireTime, signature);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - 已过期返回 false")
        void verifySignature_shouldReturnFalse_whenExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString().replace("-", "");
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 1 小时前
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, requestId, expireTime, signature);

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
            String requestId = UUID.randomUUID().toString().replace("-", "");

            // When
            signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), any(Duration.class), paramsCaptor.capture());

            Map<String, String> params = paramsCaptor.getValue();
            assertThat(params).containsEntry("pid", "200");
            assertThat(params).containsEntry("rid", requestId);
            assertThat(params).hasSize(2);
        }
    }
}
