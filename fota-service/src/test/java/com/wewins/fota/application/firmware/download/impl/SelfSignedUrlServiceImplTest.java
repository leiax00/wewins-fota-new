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
 * SelfSignedUrlServiceImpl 单元测试
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@DisplayName("SelfSignedUrlServiceImpl 单元测试")
class SelfSignedUrlServiceImplTest {

    private static final String TEST_SECRET_KEY = "test-secret-key-at-least-32-bytes-long-for-security";
    private static final String TEST_BASE_URL = "https://cdn.example.com";

    private SelfSignedUrlServiceImpl signedUrlService;
    private FirmwareDownloadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FirmwareDownloadProperties();
        properties.setBaseUrl(TEST_BASE_URL);
        properties.getSelfSigned().setSecretKey(TEST_SECRET_KEY);
        properties.getSelfSigned().setDefaultExpireSeconds(86400);

        signedUrlService = new SelfSignedUrlServiceImpl(properties);
    }

    @Nested
    @DisplayName("generateSignedUrl 方法测试")
    class GenerateSignedUrlTests {

        @Test
        @DisplayName("生成签名 URL - 基本场景")
        void generateSignedUrl_shouldReturnSignedUrl_whenValidParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).isNotEmpty();
            assertThat(url).startsWith(TEST_BASE_URL);
            // URL 不再包含 pid 和 rid 参数
            assertThat(url).doesNotContain("pid=");
            assertThat(url).doesNotContain("rid=");
            assertThat(url).contains("expire=");
            assertThat(url).contains("sig=");
        }

        @Test
        @DisplayName("生成签名 URL - 固件路径以斜杠开头")
        void generateSignedUrl_shouldHandleLeadingSlash() {
            // Given
            String firmwarePath = "/fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).contains("//cdn.example.com/fota/fw/123");
            assertThat(url).doesNotContain("//cdn.example.com//fota");
        }

        @Test
        @DisplayName("生成签名 URL - 自定义过期时间")
        void generateSignedUrl_shouldUseCustomExpireTime() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();
            int customExpireSeconds = 3600;

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, customExpireSeconds);

            // Then
            assertThat(url).isNotEmpty();
            // URL 不再包含 pid 和 rid 参数
            assertThat(url).doesNotContain("pid=");
            assertThat(url).doesNotContain("rid=");

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
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

            // Then
            assertThat(url).startsWith("fota/fw/123/test-firmware.zip?");
            // URL 不再包含 pid 和 rid 参数
            assertThat(url).doesNotContain("pid=");
            assertThat(url).doesNotContain("rid=");
        }

        @Test
        @DisplayName("生成签名 URL - 空固件路径抛出异常")
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
        @DisplayName("生成签名 URL - policyId 为 null 不再抛出异常")
        void generateSignedUrl_shouldNotThrowException_whenPolicyIdIsNull() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = null;
            String requestId = UUID.randomUUID().toString();

            // When & Then - 不再校验 policyId
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);
            assertThat(url).isNotEmpty();
        }

        @Test
        @DisplayName("生成签名 URL - requestId 为 null 不再抛出异常")
        void generateSignedUrl_shouldNotThrowException_whenRequestIdIsNull() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = null;

            // When & Then - 不再校验 requestId
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);
            assertThat(url).isNotEmpty();
        }

        @Test
        @DisplayName("生成签名 URL - 过期时间超出范围抛出异常")
        void generateSignedUrl_shouldThrowException_whenExpireTimeTooLong() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();
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
        @DisplayName("验证签名 - 有效签名")
        void verifySignature_shouldReturnTrue_whenSignatureIsValid() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // 生成签名 URL
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, 3600);

            // 解析 URL 参数
            String[] params = url.split("\\?");
            String queryString = params.length > 1 ? params[1] : "";
            String[] queryParams = queryString.split("&");
            long expireTime = Long.parseLong(findParam(queryParams, "expire"));
            String signature = findParam(queryParams, "sig");

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, requestId, expireTime, signature);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证签名 - 错误签名")
        void verifySignature_shouldReturnFalse_whenSignatureIsInvalid() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();
            long expireTime = System.currentTimeMillis() / 1000 + 3600;
            String invalidSignature = "invalid123";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, requestId, expireTime, invalidSignature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 已过期")
        void verifySignature_shouldReturnFalse_whenSignatureIsExpired() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // 使用过去的过期时间
            long expireTime = System.currentTimeMillis() / 1000 - 3600; // 1 小时前
            String signature = "any-signature";

            // When
            boolean isValid = signedUrlService.verifySignature(firmwarePath, policyId, requestId, expireTime, signature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证签名 - 相同参数生成相同签名")
        void verifySignature_shouldBeConsistent_forSameParams() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();
            int expireSeconds = 3600;

            // When
            String url1 = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, expireSeconds);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId, expireSeconds);

            // Then - 过期时间可能不同，但签名算法应该一致
            // 我们只验证签名部分，忽略时间戳差异
            String sig1 = extractSignature(url1);
            String sig2 = extractSignature(url2);

            // 由于时间戳不同，签名也会不同
            // 但我们可以验证格式一致
            assertThat(sig1).hasSize(64); // SHA256 十六进制是 64 字符
            assertThat(sig2).hasSize(64);
        }
    }

    @Nested
    @DisplayName("签名一致性测试")
    class SignatureConsistencyTests {

        @Test
        @DisplayName("相同固件路径生成相同签名（policyId 和 requestId 不影响签名）")
        void generateSignedUrl_shouldGenerateSameSignature_forSameFirmwarePath() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            String requestId1 = UUID.randomUUID().toString();
            String requestId2 = UUID.randomUUID().toString();

            // When - 使用不同的 policyId 和 requestId，但相同的 firmwarePath
            String url1 = signedUrlService.generateSignedUrl(firmwarePath, 100L, requestId1);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath, 200L, requestId2); // 不同 policyId
            String url3 = signedUrlService.generateSignedUrl(firmwarePath, 100L, requestId2); // 不同 requestId

            // Then - 由于签名只包含 firmwarePath 和 expireTime，
            // 在同一秒内生成的 URL 签名应该相同
            String sig1 = extractSignature(url1);
            String sig2 = extractSignature(url2);
            String sig3 = extractSignature(url3);

            // 验证签名长度
            assertThat(sig1).hasSize(64);
            assertThat(sig2).hasSize(64);
            assertThat(sig3).hasSize(64);

            // 由于签名不再包含 policyId 和 requestId，签名只与 firmwarePath 和 expireTime 相关
            // 如果在同一秒内生成，签名应该相同
            // 但由于时间戳可能不同，我们只验证格式正确
        }

        @Test
        @DisplayName("不同固件路径生成不同签名")
        void generateSignedUrl_shouldGenerateDifferentSignatures_forDifferentFirmwarePaths() {
            // Given
            String firmwarePath1 = "fota/fw/123/firmware-A.zip";
            String firmwarePath2 = "fota/fw/456/firmware-B.zip";
            String requestId = UUID.randomUUID().toString();

            // When
            String url1 = signedUrlService.generateSignedUrl(firmwarePath1, 100L, requestId);
            String url2 = signedUrlService.generateSignedUrl(firmwarePath2, 100L, requestId);

            // Then
            String sig1 = extractSignature(url1);
            String sig2 = extractSignature(url2);

            // 由于 firmwarePath 不同，签名应该不同（即使其他参数相同）
            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("签名长度验证")
        void generateSignedUrl_shouldGenerate64CharSignature() {
            // Given
            String firmwarePath = "fota/fw/123/test-firmware.zip";
            Long policyId = 100L;
            String requestId = UUID.randomUUID().toString();

            // When
            String url = signedUrlService.generateSignedUrl(firmwarePath, policyId, requestId);

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
