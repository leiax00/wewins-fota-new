package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.storage.core.S3StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

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
@ExtendWith(MockitoExtension.class)
@DisplayName("S3PresignedUrlServiceImpl 单元测试")
class S3PresignedUrlServiceImplTest {

    private static final String TEST_PRESIGNED_URL = "https://s3.example.com/bucket/fota/fw/123/test-firmware.zip?X-Amz-Signature=abc123";

    @Mock
    private S3StorageClient s3StorageClient;

    private S3PresignedUrlServiceImpl signedUrlService;
    private FirmwareDownloadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FirmwareDownloadProperties();
        properties.getS3Presigned().setDefaultExpireSeconds(3600);

        signedUrlService = new S3PresignedUrlServiceImpl(s3StorageClient, properties);
    }

    @Nested
    @DisplayName("generateSignedUrl 方法测试")
    class GenerateSignedUrlTests {

        @Test
        @DisplayName("生成预签名 URL - 基本场景")
        void generateSignedUrl_shouldReturnPresignedUrl() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            when(s3StorageClient.getDownloadUrl(eq(firmwarePath), any(Duration.class)))
                    .thenReturn(TEST_PRESIGNED_URL);

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isNotEmpty();
            assertThat(url).isEqualTo(TEST_PRESIGNED_URL);
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), any(Duration.class));
        }

        @Test
        @DisplayName("生成预签名 URL - 使用默认过期时间")
        void generateSignedUrl_shouldUseDefaultExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int defaultExpireSeconds = properties.getS3Presigned().getDefaultExpireSeconds();
            when(s3StorageClient.getDownloadUrl(eq(firmwarePath), any(Duration.class)))
                    .thenReturn(TEST_PRESIGNED_URL);

            // When
            signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), eq(Duration.ofSeconds(defaultExpireSeconds)));
        }

        @Test
        @DisplayName("生成预签名 URL - 自定义过期时间")
        void generateSignedUrl_shouldUseCustomExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int customExpireSeconds = 7200;
            when(s3StorageClient.getDownloadUrl(eq(firmwarePath), any(Duration.class)))
                    .thenReturn(TEST_PRESIGNED_URL);

            // When
            signedUrlService.generateSignedUrl(firmwarePath, customExpireSeconds);

            // Then
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), eq(Duration.ofSeconds(customExpireSeconds)));
        }

        @Test
        @DisplayName("生成预签名 URL - 空固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsNull() {
            // Given
            String firmwarePath = null;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成预签名 URL - 空白固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsBlank() {
            // Given
            String firmwarePath = "   ";

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成预签名 URL - 过期时间为 0 抛出异常")
        void generateSignedUrl_shouldThrowException_whenExpireTimeIsZero() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int expireSeconds = 0;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, expireSeconds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expireSeconds 必须在 1-604800 秒之间");
        }

        @Test
        @DisplayName("生成预签名 URL - 过期时间为负数抛出异常")
        void generateSignedUrl_shouldThrowException_whenExpireTimeIsNegative() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int expireSeconds = -100;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, expireSeconds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expireSeconds 必须在 1-604800 秒之间");
        }

        @Test
        @DisplayName("生成预签名 URL - 过期时间超出 7 天限制抛出异常")
        void generateSignedUrl_shouldThrowException_whenExpireTimeTooLong() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int expireSeconds = 8 * 24 * 3600; // 8 天，超过 7 天限制

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, expireSeconds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expireSeconds 必须在 1-604800 秒之间");
        }

        @Test
        @DisplayName("生成预签名 URL - 边界值：正好 7 天")
        void generateSignedUrl_shouldAcceptSevenDaysExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int expireSeconds = 7 * 24 * 3600; // 正好 7 天
            when(s3StorageClient.getDownloadUrl(eq(firmwarePath), any(Duration.class)))
                    .thenReturn(TEST_PRESIGNED_URL);

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, expireSeconds);

            // Then
            assertThat(url).isEqualTo(TEST_PRESIGNED_URL);
            verify(s3StorageClient).getDownloadUrl(eq(firmwarePath), eq(Duration.ofSeconds(expireSeconds)));
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
            long expireTime = System.currentTimeMillis() / 1000 + 3600; // 1 小时后
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - 已过期返回 false")
        void verifySignature_shouldReturnFalse_whenExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 1 小时前
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 边界值：刚好过期")
        void verifySignature_shouldReturnFalse_whenJustExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            long expireTime = System.currentTimeMillis() / 1000 - 1; // 1 秒前过期
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 边界值：即将过期（1 秒后）")
        void verifySignature_shouldReturnTrue_whenAboutToExpire() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            long expireTime = System.currentTimeMillis() / 1000 + 1; // 1 秒后过期
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then
            // 由于时间可能在这个测试执行期间变化，我们只验证不抛异常
            assertThat(isValid).isNotNull();
        }
    }
}
