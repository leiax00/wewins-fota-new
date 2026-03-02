package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.storage.config.StorageProperties;
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
 * 继承 {@link NoneSignedUrlServiceImpl}，复用 URL 基础路径构建逻辑，
 * 在此基础上添加 HMAC-SHA256 签名参数。
 * </p>
 * <p>
 * URL 格式：{baseUrl}/{bucket}/{firmwarePath}?expire={expireTime}&sig={signature}
 * </p>
 * <p>
 * 签名内容：{完整URL}|{expireTime}，其中完整 URL 包含域名、bucket 和路径，
 * 可防止域名被篡改。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Slf4j
public class SelfSignedUrlServiceImpl extends NoneSignedUrlServiceImpl {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_DELIMITER = "|";

    public SelfSignedUrlServiceImpl(FirmwareDownloadProperties properties, StorageProperties storageProperties) {
        super(properties, storageProperties);
    }

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

        // 构建签名载荷
        String noSignUrl = buildBaseUrl(firmwarePath);
        String payload = buildPayload(noSignUrl, expireTime);

        // 生成 HMAC-SHA256 签名
        String signature = hmacSha256(payload);

        // 复用父类方法构建基础 URL，再添加签名参数
        return noSignUrl + "?expire=" + expireTime + "&sig=" + signature;
    }

    @Override
    public boolean verifySignature(String firmwarePath, long expireTime, String signature) {
        // 检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (expireTime < currentTime) {
            log.debug("签名 URL 已过期: expireTime={}, currentTime={}", expireTime, currentTime);
            return false;
        }

        // 重新计算签名并比对（使用完整 URL）
        String noSignUrl = buildBaseUrl(firmwarePath);
        String payload = buildPayload(noSignUrl, expireTime);
        String expectedSignature = hmacSha256(payload);

        return signature.equals(expectedSignature);
    }

    /**
     * 参数校验
     */
    private void validateParams(String firmwarePath, int expireSeconds) {
        validateFirmwarePath(firmwarePath);
        if (expireSeconds <= 0 || expireSeconds > Duration.ofDays(7).toSeconds()) {
            throw new IllegalArgumentException("expireSeconds 必须在 1-604800 秒之间（最多 7 天）");
        }
    }

    /**
     * 构建签名载荷
     * <p>
     * 格式: noSignUrl|expireTime
     * </p>
     *
     * @param noSignUrl  不含签名参数的完整 URL（包含域名、bucket、路径）
     * @param expireTime 过期时间戳
     */
    private String buildPayload(String noSignUrl, long expireTime) {
        return noSignUrl + SIGNATURE_DELIMITER + expireTime;
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
}
