package com.wewins.fota.infra.security;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 分区密钥轮换服务
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
public class RegionRotateKeyService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RegionRotateKey getPendingRotateKey(String regionCode) {
        String key = String.format(RedisKeyConstants.REGION_ROTATE_KEY_TEMPLATE, regionCode);
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof RegionRotateKey) {
            return (RegionRotateKey) value;
        }
        return null;
    }

    public void acknowledgeRotateKey(String regionCode, String keyId) {
        RegionRotateKey pending = getPendingRotateKey(regionCode);
        if (pending == null) {
            return;
        }
        if (pending.getKeyId() == null || !pending.getKeyId().equals(keyId)) {
            return;
        }
        String key = String.format(RedisKeyConstants.REGION_ROTATE_KEY_TEMPLATE, regionCode);
        redisTemplate.delete(key);
    }
}
