package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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

    private NoneSignedUrlServiceImpl signedUrlService;
    private FirmwareDownloadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FirmwareDownloadProperties();
        properties.setBaseUrl(TEST_BASE_URL);

        signedUrlService = new NoneSignedUrlServiceImpl(properties);
    }

    @Nested
    @DisplayName("generateSignedUrl 方法测试")
    class GenerateSignedUrlTests {

        @Test
        @DisplayName("生成无签名 URL - 基本场景")
        void generateSignedUrl_shouldReturnUnsignedUrl_whenValidParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).isEqualTo("https://cdn.example.com/fota/fw/123/test-firmware.zip");
            // 不包含签名参数
            assertThat(url).doesNotContain("sig=");
            assertThat(url).doesNotContain("expire=");
            assertThat(url).doesNotContain("pid=");
            assertThat(url).doesNotContain("rid=");
        }

        @Test
        @DisplayName("生成无签名 URL - 固件路径以斜杠开头")
        void generateSignedUrl_shouldHandleLeadingSlash() {
            // Given
            String firmwarePath = "/fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

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
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).isEqualTo("https://cdn.example.com/fota/fw/123/test-firmware.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - 无基础 URL")
        void generateSignedUrl_shouldWorkWithoutBaseUrl() {
            // Given
            properties.setBaseUrl(null);
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).isEqualTo("fota/fw/123/test-firmware.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - 空 baseUrl")
        void generateSignedUrl_shouldWorkWithBlankBaseUrl() {
            // Given
            properties.setBaseUrl("  ");
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).isEqualTo("fota/fw/123/test-firmware.zip");
        }

        @Test
        @DisplayName("生成无签名 URL - 空固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsNull() {
            // Given
            String firmwarePath = null;
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成无签名 URL - 空白固件路径抛出异常")
        void generateSignedUrl_shouldThrowException_whenFirmwarePathIsBlank() {
            // Given
            String firmwarePath = "  ";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When & Then
            assertThatThrownBy(() -> signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("firmwarePath 不能为空");
        }

        @Test
        @DisplayName("生成无签名 URL - policyId 和 requestId 不影响结果")
        void generateSignedUrl_shouldIgnorePolicyIdAndRequestId() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";

            // When - 使用不同的 policyId 和 requestId
            String url1 = signedUrlService.generateSignedUrl(firmwarePath, 100L, "request-1");
            String url2 = signedUrlService.generateSignedUrl(firmwarePath, 200L, "request-2");
            String url3 = signedUrlService.generateSignedUrl(firmwarePath, null, null);

            // Then - 结果应该相同
            assertThat(url1).isEqualTo(url2);
            assertThat(url2).isEqualTo(url3);
        }

        @Test
        @DisplayName("生成无签名 URL - expireSeconds 参数不影响结果")
        void generateSignedUrl_shouldIgnoreExpireSeconds() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When - 使用不同的过期时间
            String url1 = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, 3600);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, 86400);

            // Then - 结果应该相同（无签名模式不包含过期时间）
            assertThat(url1).isEqualTo(url2);
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
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 已过期
            String signature = "invalid-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, requestId, expireTime, signature);

            // Then - 无签名模式始终返回 true
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - firmwarePath 为空返回 false")
        void verifySignature_shouldReturnFalse_whenFirmwarePathIsNull() {
            // When & Then
            assertThat(signedUrlService.verifySignature(null, null, null, 0, null)).isFalse();
            assertThat(signedUrlService.verifySignature("  ", 1L, "id", 123, "sig")).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 有效 firmwarePath 返回 true")
        void verifySignature_shouldReturnTrue_forValidFirmwarePath() {
            // When & Then
            assertThat(signedUrlService.verifySignature("path", 1L, "id", 123, "sig")).isTrue();
            assertThat(signedUrlService.verifySignature("/fota/fw/test.zip", null, null, 0, null)).isTrue();
        }
    }
}
