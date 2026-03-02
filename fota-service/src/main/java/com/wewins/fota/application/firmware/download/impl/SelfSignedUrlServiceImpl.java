package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * 自签名下载 URL 服务实现。
 * <p>
 * 使用 HMAC-SHA256 算法生成签名，URL 格式：
 * <pre>{baseUrl}/{firmwarePath}?expire={expireTime}&sig={signature}</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Slf4j
@RequiredArgsConstructor
public class SelfSignedUrlServiceImpl implements SignedUrlService {

    private final FirmwareDownloadProperties properties;

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_DELIMITER = "|";

    @Override
    public String generateSignedUrl(String firmwarePath) {
        return generateSignedUrl(firmwarePath, properties.getSelfSigned().getDefaultExpireSeconds());
    }

    @Override
    public String generateSignedUrl(String firmwarePath, int expireSeconds) {
        // 参数校验
        validateParams(firmwarePath, expireSeconds);

        // 计算过期时间戳（秒）
        long expireTime = System.currentTimeMillis() / 1000 + expireSeconds;

        // 构建签名载荷（不再包含 policyId 和 requestId）
        String payload = buildPayload(firmwarePath, expireTime);

        // 生成 HMAC-SHA256 签名
        String signature = hmacSha256(payload);

        // 构建完整 URL（不再包含 pid 和 rid 参数）
        return buildUrl(firmwarePath, expireTime, signature);
    }

    @Override
    public boolean verifySignature(String firmwarePath, long expireTime, String signature) {
        // 检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (expireTime < currentTime) {
            log.debug("签名 URL 已过期: expireTime={}, currentTime={}", expireTime, currentTime);
            return false;
        }

        // 重新计算签名并比对（不再包含 policyId 和 requestId）
        String payload = buildPayload(firmwarePath, expireTime);
        String expectedSignature = hmacSha256(payload);

        return signature.equals(expectedSignature);
    }

    /**
     * 参数校验
     * <p>
     * 注意：policyId 和 requestId 参数保留用于接口兼容性，但不再参与签名计算。
     * </p>
     */
    private void validateParams(String firmwarePath, int expireSeconds) {
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException("firmwarePath 不能为空");
        }
        // policyId 和 requestId 不再参与签名，仅保留参数兼容性
        if (expireSeconds <= 0 || expireSeconds > Duration.ofDays(7).toSeconds()) {
            throw new IllegalArgumentException("expireSeconds 必须在 1-604800 秒之间（最多 7 天）");
        }
    }

    /**
     * 构建签名载荷
     * <p>
     * 格式: firmwarePath|expireTime
     * </p>
     */
    private String buildPayload(String firmwarePath, long expireTime) {
        return firmwarePath + SIGNATURE_DELIMITER + expireTime;
    }

    /**
     * 生成 HMAC-SHA256 签名
     *
     * @param payload 待签名数据
     * @return 十六进制签名字符串
     */
    private String hmacSha256(String payload) {
        try {
            String secretKey = properties.getSelfSigned().getSecretKey();
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("签名密钥未配置: app.firmware.download.self-signed.secret-key");
            }

            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串（小写）
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC-SHA256 算法不可用", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("签名密钥无效", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 构建完整的下载 URL
     * <p>
     * 格式: {baseUrl}/{firmwarePath}?expire={expireTime}&sig={signature}
     * </p>
     */
    private String buildUrl(String firmwarePath, long expireTime, String signature) {
        StringBuilder url = new StringBuilder();

        // 添加基础 URL 和路径
        String baseUrl = properties.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            // 移除 baseUrl 末尾的斜杠
            String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            // 确保 firmwarePath 以斜杠开头
            String cleanPath = firmwarePath.startsWith("/") ? firmwarePath : "/" + firmwarePath;
            url.append(cleanBaseUrl).append(cleanPath);
        } else {
            url.append(firmwarePath);
        }

        // 添加查询参数（不再包含 pid 和 rid）
        url.append("?expire=").append(expireTime);
        url.append("&sig=").append(signature);

        return url.toString();
    }
}
