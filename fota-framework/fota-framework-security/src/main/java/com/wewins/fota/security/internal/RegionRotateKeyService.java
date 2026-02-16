package com.wewins.fota.security.internal;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Region rotate-key service.
 */
@Service
@RequiredArgsConstructor
public class RegionRotateKeyService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RegionRotateKey getPendingRotateKey(String regionCode) {
        String key = String.format(RedisKeyConstants.REGION_ROTATE_KEY_TEMPLATE, regionCode);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof RegionRotateKey rotateKey) {
                return rotateKey;
            }
        } catch (Exception ex) {
            // Compatibility fallback: old payload may still contain the legacy class name.
            redisTemplate.delete(key);
        }
        return null;
    }

    public void acknowledgeRotateKey(String regionCode, String keyId) {
        RegionRotateKey pending = getPendingRotateKey(regionCode);
        if (pending == null || pending.getKeyId() == null || !pending.getKeyId().equals(keyId)) {
            return;
        }
        String key = String.format(RedisKeyConstants.REGION_ROTATE_KEY_TEMPLATE, regionCode);
        redisTemplate.delete(key);
    }
}
