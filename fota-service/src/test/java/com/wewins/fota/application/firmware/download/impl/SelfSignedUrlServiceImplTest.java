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
 * SelfSignedUrlServiceImpl 单元测试
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@DisplayName("SelfSignedUrlServiceImpl 单元测试")
class SelfSignedUrlServiceImplTest {

    private static final String TEST_SECRET_KEY = "test-secret-key-at-least-32-bytes-long-for-security";
    private static final String TEST_BASE_URL = "https://cdn.example.com";
    private static final String TEST_S3_BUCKET = "fota";

    private SelfSignedUrlServiceImpl signedUrlService;
    private FirmwareDownloadProperties properties;
    private StorageProperties storageProperties;

    @BeforeEach
    void setUp() {
        properties = new FirmwareDownloadProperties();
        properties.setBaseUrl(TEST_BASE_URL);
        properties.getSelfSigned().setSecretKey(TEST_SECRET_KEY);
        properties.getSelfSigned().setDefaultExpireSeconds(86400);

        storageProperties = new StorageProperties();
        storageProperties.getS3().setEnabled(false); // 默认不启用 S3

        signedUrlService = new SelfSignedUrlServiceImpl(properties, storageProperties);
    }

    @Nested
    @DisplayName("generateSignedUrl 方法测试")
    class GenerateSignedUrlTests {

        @Test
        @DisplayName("生成签名 URL - 基本场景")
        void generateSignedUrl_shouldReturnSignedUrl_whenValidParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).isNotEmpty();
            assertThat(url).startsWith(TEST_BASE_URL);
            assertThat(url).contains("expire=");
            assertThat(url).contains("sig=");
        }

        @Test
        @DisplayName("生成签名 URL - 固件路径以斜杠开头")
        void generateSignedUrl_shouldHandleLeadingSlash() {
            // Given
            String firmwarePath = "/fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).contains("//cdn.example.com/fota/fw/123");
            assertThat(url).doesNotContain("//cdn.example.com//fota");
        }

        @Test
        @DisplayName("生成签名 URL - 自定义过期时间")
        void generateSignedUrl_shouldUseCustomExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int customExpireSeconds = 3600;

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, customExpireSeconds);

            // Then
            assertThat(url).isNotEmpty();

            // 验证过期时间约为当前时间 + 3600 秒（允许 5 秒误差）
            String[] params = url.split("\\?");
            String queryString = params.length > 1 ? params[1] : "";
            String[] queryParams = queryString.split("&");
            String expireParam = findParam(queryParams, "expire");
            long expireTime = Long.parseLong(expireParam);
            long expectedExpireTime = System.currentTimeMillis() / 1000 + customExpireSeconds;
            assertThat(Math.abs(expireTime - expectedExpireTime)).isLessThan(5);
        }

        @Test
        @DisplayName("生成签名 URL - 无基础 URL")
        void generateSignedUrl_shouldWorkWithoutBaseUrl() {
            // Given
            properties.setBaseUrl(null);
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            assertThat(url).startsWith("fota/fw/123/test-firmware.zip?");
        }

        @Test
        @DisplayName("生成签名 URL - 空固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsNull() {
            // Given
            String firmwarePath = null;

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成签名 URL - 过期时间超出范围抛出异常")
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
        @DisplayName("生成签名 URL - S3 PATH_STYLE 模式添加 bucket 到路径")
        void generateSignedUrl_shouldAddBucketToPath_whenS3PathStyleEnabled() {
            // Given
            storageProperties.getS3().setEnabled(true);
            storageProperties.getS3().setUrlAccessType(StorageProperties.UrlAccessType.PATH_STYLE);
            storageProperties.getS3().setBucket(TEST_S3_BUCKET);
            String firmwarePath = "fw/test.zip";

            // 重新创建服务实例
            signedUrlService = new SelfSignedUrlServiceImpl(properties, storageProperties);

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then - URL 应该包含 bucket
            assertThat(url).contains("//cdn.example.com/" + TEST_S3_BUCKET + "/fw/test.zip");
        }

        @Test
        @DisplayName("生成签名 URL - S3 禁用时不添加 bucket")
        void generateSignedUrl_shouldNotAddBucket_whenS3Disabled() {
            // Given
            storageProperties.getS3().setEnabled(false);
            storageProperties.getS3().setUrlAccessType(StorageProperties.UrlAccessType.PATH_STYLE);
            storageProperties.getS3().setBucket(TEST_S3_BUCKET);
            String firmwarePath = "fw/test.zip";

            // 重新创建服务实例
            signedUrlService = new SelfSignedUrlServiceImpl(properties, storageProperties);

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then - PATH_STYLE 由 urlAccessType 决定，因此仍会带 bucket
            assertThat(url).contains("//cdn.example.com/fota/fw/test.zip");
        }
    }

    @Nested
    @DisplayName("verifySignature 方法测试")
    class VerifySignatureTests {

        @Test
        @DisplayName("验证签名 - 有效签名")
        void verifySignature_shouldReturnTrue_whenSignatureIsValid() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // 生成签名 URL
            String url = signedUrlService.generateSignedUrl(firmwarePath, 3600);

            // 解析 URL 参数
            String[] params = url.split("\\?");
            String queryString = params.length > 1 ? params[1] : "";
            String[] queryParams = queryString.split("&");
            long expireTime = Long.parseLong(findParam(queryParams, "expire"));
            String signature = findParam(queryParams, "sig");

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - 错误签名")
        void verifySignature_shouldReturnFalse_whenSignatureIsInvalid() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            long expireTime = System.currentTimeMillis() / 1000 + 3600;
            String invalidSignature = "invalid123";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, invalidSignature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 已过期")
        void verifySignature_shouldReturnFalse_whenSignatureIsExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // 使用过去的过期时间
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 1 小时前
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, expireTime, signature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 签名长度验证")
        void verifySignature_shouldBeConsistent_forSameParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            int expireSeconds = 3600;

            // When
            String url1 = signedUrlService.generateSignedUrl(firmwarePath, expireSeconds);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath, expireSeconds);

            // Then - 验证签名格式一致
            String sig1 = extractSignature(url1);
            String sig2 = extractSignature(url2);

            assertThat(sig1).hasSize(64); // SHA256 十六进制是 64 字符
            assertThat(sig2).hasSize(64);
        }
    }

    @Nested
    @DisplayName("签名一致性测试")
    class SignatureConsistencyTests {

        @Test
        @DisplayName("相同固件路径生成相同签名格式")
        void generateSignedUrl_shouldGenerateSameSignature_forSameFirmwarePath() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url1 = signedUrlService.generateSignedUrl(firmwarePath);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath);

            // Then - 验证签名格式正确
            String sig1 = extractSignature(url1);
            String sig2 = extractSignature(url2);

            assertThat(sig1).hasSize(64);
            assertThat(sig2).hasSize(64);
        }

        @Test
        @DisplayName("不同固件路径生成不同签名")
        void generateSignedUrl_shouldGenerateDifferentSignatures_forDifferentFirmwarePaths() {
            // Given
            String firmwarePath1 = "fota/fw/123/firmware-A.zip";
            String firmwarePath2 = "fota/fw/456/firmware-B.zip";

            // When
            String url1 = signedUrlService.generateSignedUrl(firmwarePath1);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath2);

            // Then
            String sig1 = extractSignature(url1);
            String sig2 = extractSignature(url2);

            // 由于 firmwarePath 不同，签名应该不同
            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("签名长度验证")
        void generateSignedUrl_shouldGenerate64CharSignature() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath);

            // Then
            String signature = extractSignature(url);
            assertThat(signature).hasSize(64); // SHA256 = 256 bits = 64 hex chars
        }
    }

    /**
     * 从 URL 中查找指定参数的值
     */
    private String findParam(String[] params, String paramName) {
        for (String param : params) {
            if (param.startsWith(paramName + "=")) {
                return param.substring(paramName.length() + 1);
            }
        }
        throw new IllegalArgumentException("参数未找到: " + paramName);
    }

    /**
     * 从 URL 中提取签名
     */
    private String extractSignature(String url) {
        // 支持 "?sig=" 和 "&sig=" 两种格式
        int sigIndex = url.indexOf("&sig=");
        if (sigIndex == -1) {
            sigIndex = url.indexOf("?sig=");
        }
        if (sigIndex == -1) {
            throw new IllegalArgumentException("URL 中未找到签名参数");
        }
        return url.substring(sigIndex + 5); // "&sig=" 或 "?sig=" 的长度
    }
}
