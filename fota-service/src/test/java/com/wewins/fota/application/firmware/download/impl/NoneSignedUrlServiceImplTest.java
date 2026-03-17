package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.storage.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NoneSignedUrlServiceImpl 单元测试
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@DisplayName("NoneSignedUrlServiceImpl 单元测试")
class NoneSignedUrlServiceImplTest {

    private static final String TEST_BASE_URL = "https://cdn.example.com";
    private static final String TEST_S3_BUCKET = "fota";

    private NoneSignedUrlServiceImpl signedUrlService;
    private FirmwareDownloadProperties properties;
    private StorageProperties storageProperties;

    @BeforeEach
    void setUp() {
        properties = new FirmwareDownloadProperties();
        properties.setBaseUrl(TEST_BASE_URL);

        storageProperties = new StorageProperties();
        storageProperties.getS3().setEnabled(false); // 默认不启用 S3

        signedUrlService = new NoneSignedUrlServiceImpl(properties, storageProperties);
    }

    @Nested
    @DisplayName("generateSignedUrl 方法测试")
    class GenerateSignedUrlTests {

        @Test
        @DisplayName("生成无签名 URL - 基本场景")
        void generateSignedUrl_shouldReturnUnsignedUrl_whenValidParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isEqualTo("https://cdn.example.com/fota/fw/123/test-firmware.zip");
            // 不包含签名参数
            assertThat(url).doesNotContain("sig=");
            assertThat(url).doesNotContain("expire=");
        }

        @Test
        @DisplayName("生成无签名 URL - 固件路径以斜杠开头")
        void generateSignedUrl_shouldHandleLeadingSlash() {
            // Given
            String firmwarePath = "/fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isEqualTo("https://cdn.example.com/fota/fw/123/test-firmware.zip");
            assertThat(url).doesNotContain("//cdn.example.com//fota");
        }

        @Test
        @DisplayName("生成无签名 URL - baseUrl 以斜杠结尾")
        void generateSignedUrl_shouldHandleTrailingSlashInBaseUrl() {
            // Given
            properties.setBaseUrl("https://cdn.example.com/");
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isEqualTo("https://cdn.example.com/fota/fw/123/test-firmware.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - 无基础 URL")
        void generateSignedUrl_shouldWorkWithoutBaseUrl() {
            // Given
            properties.setBaseUrl(null);
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isEqualTo("fota/fw/123/test-firmware.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - 空 baseUrl")
        void generateSignedUrl_shouldWorkWithBlankBaseUrl() {
            // Given
            properties.setBaseUrl("  ");
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isEqualTo("fota/fw/123/test-firmware.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - 空固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsNull() {
            // Given
            String firmwarePath = null;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成无签名 URL - 空白固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsBlank() {
            // Given
            String firmwarePath = "  ";

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成无签名 URL - expireSeconds 参数不影响结果")
        void generateSignedUrl_shouldIgnoreExpireSeconds() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When - 使用不同的过期时间
            String url1 = signedUrlService.generateSignedUrl(firmwarePath, 3600);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath, 86400);

            // Then - 结果应该相同（无签名模式不包含过期时间）
            assertThat(url1).isEqualTo(url2);
        }

        @Test
        @DisplayName("生成无签名 URL - S3 path_style 模式添加 bucket 到路径")
        void generateSignedUrl_shouldAddBucketToPath_whenS3PathStyleEnabled() {
            // Given
            storageProperties.getS3().setEnabled(true);
            storageProperties.getS3().setPathStyleAccessEnabled(true);
            storageProperties.getS3().setBucket(TEST_S3_BUCKET);
            String firmwarePath = "fw/test.zip";

            // 重新创建服务实例
            signedUrlService = new NoneSignedUrlServiceImpl(properties, storageProperties);

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then - URL 应该包含 bucket
            assertThat(url).isEqualTo("https://cdn.example.com/" + TEST_S3_BUCKET + "/fw/test.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - S3 禁用时不添加 bucket")
        void generateSignedUrl_shouldNotAddBucket_whenS3Disabled() {
            // Given
            storageProperties.getS3().setEnabled(false);
            storageProperties.getS3().setPathStyleAccessEnabled(true);
            storageProperties.getS3().setBucket(TEST_S3_BUCKET);
            String firmwarePath = "fw/test.zip";

            // 重新创建服务实例
            signedUrlService = new NoneSignedUrlServiceImpl(properties, storageProperties);

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then - URL 不应该包含额外的 bucket
            assertThat(url).isEqualTo("https://cdn.example.com/fw/test.zip");
        }
    }

    @Nested
    @DisplayName("verifySignature 方法测试")
    class VerifySignatureTests {

        @Test
        @DisplayName("验证签名 - 始终返回 true")
        void verifySignature_shouldAlwaysReturnTrue() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 已过期
            String signature = "invalid-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then - 无签名模式始终返回 true
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - firmwarePath 为空返回 false")
        void verifySignature_shouldReturnFalse_whenFirmwarePathIsNull() {
            // When & Then
            assertThat(signedUrlService.verifySignature(null, 0, null)).isFalse();
            assertThat(signedUrlService.verifySignature("  ", 123, "sig")).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 有效 firmwarePath 返回 true")
        void verifySignature_shouldReturnTrue_forValidFirmwarePath() {
            // When & Then
            assertThat(signedUrlService.verifySignature("path", 123, "sig")).isTrue();
            assertThat(signedUrlService.verifySignature("/fota/fw/test.zip", 0, null)).isTrue();
        }
    }
}
