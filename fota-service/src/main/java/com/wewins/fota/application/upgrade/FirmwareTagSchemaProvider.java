package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.module.system.application.DictItemAppService;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 提供固件标签 schema 中声明的可匹配字段列表。
 * <p>
 * 升级检查是高频路径，因此这里使用进程内缓存，并在字典变更后主动失效。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirmwareTagSchemaProvider {

    public static final String TYPE_FIRMWARE_TAGS = "json_schema.firmware_tags";

    private final DictItemAppService dictItemAppService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public Set<String> getTagKeys() {
        Set<String> cached = loadFromRedis();
        if (cached != null) {
            return cached;
        }

        Set<String> resolved = loadTagKeys();
        saveToRedis(resolved);
        return resolved;
    }

    public void evictCache() {
        stringRedisTemplate.delete(RedisKeyConstants.FIRMWARE_TAG_SCHEMA_CACHE_KEY);
    }

    private Set<String> loadTagKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (DictItem item : dictItemAppService.listItemsByTypeCode(TYPE_FIRMWARE_TAGS)) {
            if (item == null || !"active".equalsIgnoreCase(item.getStatus())) {
                continue;
            }
            String value = item.getValue();
            if (value != null && !value.isBlank()) {
                keys.add(value.trim());
            }
        }
        log.info("Loaded firmware tag schema keys from dictionary: {}", keys);
        return Set.copyOf(keys);
    }

    private Set<String> loadFromRedis() {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyConstants.FIRMWARE_TAG_SCHEMA_CACHE_KEY);
            if (json == null || json.isBlank()) {
                return null;
            }
            return Set.copyOf(objectMapper.readValue(json, new TypeReference<LinkedHashSet<String>>() {
            }));
        } catch (Exception e) {
            log.warn("Failed to load firmware tag schema from Redis cache: {}", e.getMessage());
            return null;
        }
    }

    private void saveToRedis(Set<String> keys) {
        try {
            String json = objectMapper.writeValueAsString(keys);
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.FIRMWARE_TAG_SCHEMA_CACHE_KEY,
                    json,
                    RedisKeyConstants.CONFIG_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Failed to save firmware tag schema to Redis cache: {}", e.getMessage());
        }
    }
}
