package com.wewins.fota.security.internal;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Region secret query and persistence service.
 */
@Service
@RequiredArgsConstructor
public class RegionSecretService {

    private final RedisTemplate<String, Object> redisTemplate;

    public String getSecret(String regionCode) {
        String key = String.format(RedisKeyConstants.REGION_SECRET_KEY_TEMPLATE, regionCode);
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : String.valueOf(value);
    }

    public void setSecret(String regionCode, String secret) {
        String key = String.format(RedisKeyConstants.REGION_SECRET_KEY_TEMPLATE, regionCode);
        redisTemplate.opsForValue().set(key, secret);
    }
}
