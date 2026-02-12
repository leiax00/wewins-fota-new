package com.wewins.fota.infra.security;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.infra.config.AppProperties;
import com.wewins.fota.infra.region.RegionCodeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.UUID;

/**
 * 内部接口 HMAC 鉴权拦截器
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalHmacInterceptor implements HandlerInterceptor {

    private static final String HEADER_REGION_CODE = "X-Region-Code";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";

    private final AppProperties appProperties;
    private final RegionSecretService regionSecretService;
    private final RegionRotateKeyService rotateKeyService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"main".equals(appProperties.getMode())) {
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
        long skew = Math.abs(now - ts);
        if (skew > appProperties.getInternalAuth().getSkewSeconds()) {
            return reject(response, "timestamp_skew");
        }

        if (!reserveNonce(regionCode, nonce)) {
            return reject(response, "nonce_replay");
        }

        String pathWithQuery = buildPathWithQuery(request);
        String payload = HmacSigner.buildPayload(regionCode, timestamp, nonce, request.getMethod(), pathWithQuery);

        String secret = regionSecretService.getSecret(regionCode);
        boolean matched = false;
        if (!isBlank(secret)) {
            String expected = HmacSigner.sign(secret, payload);
            matched = HmacSigner.constantTimeEquals(expected, signature);
        }

        if (!matched) {
            RegionRotateKey rotateKey = rotateKeyService.getPendingRotateKey(regionCode);
            if (rotateKey != null && !isBlank(rotateKey.getSecret())) {
                String expected = HmacSigner.sign(rotateKey.getSecret(), payload);
                matched = HmacSigner.constantTimeEquals(expected, signature);
            }
        }

        if (!matched) {
            return reject(response, "invalid_signature");
        }

        return true;
    }

    private boolean reserveNonce(String regionCode, String nonce) {
        String key = String.format(RedisKeyConstants.REGION_NONCE_KEY_TEMPLATE, regionCode, nonce);
        Duration ttl = Duration.ofSeconds(appProperties.getInternalAuth().getNonceTtlSeconds());
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, UUID.randomUUID().toString(), ttl);
        return Boolean.TRUE.equals(success);
    }

    private String buildPathWithQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return uri;
        }
        return uri + "?" + query;
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
