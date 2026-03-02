package com.wewins.fota.application.firmware.download;

/**
 * 签名下载 URL 服务
 * <p>
 * 负责生成带签名的固件下载 URL，用于：
 * </p>
 * <ul>
 *   <li>安全：通过 HMAC-SHA256 签名防止 URL 被伪造</li>
 *   <li>过期控制：设置 URL 有效期</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
public interface SignedUrlService {

    /**
     * 生成签名下载 URL
     * <p>
     * URL 格式（self-signed 模式）：
     * <pre>
     * {cdnBaseUrl}/{firmwarePath}?expire={expireTime}&sig={signature}
     * </pre>
     * </p>
     *
     * @param firmwarePath 固件文件在对象存储中的路径
     * @return 签名下载 URL
     */
    String generateSignedUrl(String firmwarePath);

    /**
     * 生成签名下载 URL（自定义过期时间）
     *
     * @param firmwarePath 固件文件在对象存储中的路径
     * @param expireSeconds URL 有效期（秒）
     * @return 签名下载 URL
     */
    String generateSignedUrl(String firmwarePath, int expireSeconds);

    /**
     * 验证签名 URL 是否有效
     *
     * @param firmwarePath 固件文件路径
     * @param expireTime   过期时间戳（秒）
     * @param signature    签名值
     * @return true 如果签名有效且未过期
     */
    boolean verifySignature(String firmwarePath, long expireTime, String signature);
}
