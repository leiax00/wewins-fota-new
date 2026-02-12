package com.wewins.fota.infra.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HMAC 签名工具
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
public final class HmacSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    private HmacSigner() {
        // utility class
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 签名失败", ex);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String buildPayload(String regionCode,
                                      String timestamp,
                                      String nonce,
                                      String method,
                                      String pathWithQuery) {
        return regionCode + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + method + "\n"
                + pathWithQuery;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
