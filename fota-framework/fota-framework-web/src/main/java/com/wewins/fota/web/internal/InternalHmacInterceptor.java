package com.wewins.fota.web.internal;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.common.security.HmacSigner;
import com.wewins.fota.security.internal.InternalAuthProperties;
import com.wewins.fota.security.internal.RegionRotateKey;
import com.wewins.fota.security.internal.RegionRotateKeyService;
import com.wewins.fota.security.internal.RegionSecretService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.UUID;

/**
 * Internal API HMAC auth interceptor.
 */
@Component
@RequiredArgsConstructor
public class InternalHmacInterceptor implements HandlerInterceptor {

    private static final String HEADER_REGION_CODE = "X-Region-Code";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";

    @Value("${app.mode:main}")
    private String mode;

    private final InternalAuthProperties internalAuthProperties;
    private final RegionSecretService regionSecretService;
    private final RegionRotateKeyService rotateKeyService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"main".equals(mode)) {
            return true;
        }

        String regionCode = request.getHeader(HEADER_REGION_CODE);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (isBlank(regionCode) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return reject(response, "missing_headers");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return reject(response, "invalid_timestamp");
        }

        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > internalAuthProperties.getSkewSeconds()) {
            return reject(response, "timestamp_skew");
        }

        if (!reserveNonce(regionCode, nonce)) {
            return reject(response, "nonce_replay");
        }

        String payload = HmacSigner.buildPayload(regionCode, timestamp, nonce, request.getMethod(), buildPathWithQuery(request));

        String secret = regionSecretService.getSecret(regionCode);
        boolean matched = false;
        if (!isBlank(secret)) {
            matched = HmacSigner.constantTimeEquals(HmacSigner.sign(secret, payload), signature);
        }

        if (!matched) {
            RegionRotateKey rotateKey = rotateKeyService.getPendingRotateKey(regionCode);
            if (rotateKey != null && !isBlank(rotateKey.getSecret())) {
                matched = HmacSigner.constantTimeEquals(HmacSigner.sign(rotateKey.getSecret(), payload), signature);
            }
        }

        if (!matched) {
            return reject(response, "invalid_signature");
        }

        return true;
    }

    private boolean reserveNonce(String regionCode, String nonce) {
        String key = String.format(RedisKeyConstants.REGION_NONCE_KEY_TEMPLATE, regionCode, nonce);
        Duration ttl = Duration.ofSeconds(internalAuthProperties.getNonceTtlSeconds());
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, UUID.randomUUID().toString(), ttl);
        return Boolean.TRUE.equals(success);
    }

    private String buildPathWithQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query == null || query.isBlank()) ? uri : uri + "?" + query;
    }

    private boolean reject(HttpServletResponse response, String reason) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("X-Auth-Error", reason);
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
