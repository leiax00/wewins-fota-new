package com.wewins.fota.infra.security;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 分区密钥查询服务
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
public class RegionSecretService {

    private final RedisTemplate<String, Object> redisTemplate;

    public String getSecret(String regionCode) {
        String key = String.format(RedisKeyConstants.REGION_SECRET_KEY_TEMPLATE, regionCode);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    public void setSecret(String regionCode, String secret) {
        String key = String.format(RedisKeyConstants.REGION_SECRET_KEY_TEMPLATE, regionCode);
        redisTemplate.opsForValue().set(key, secret);
    }
}
