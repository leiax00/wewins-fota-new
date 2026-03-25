package com.wewins.fota.infra.cdn.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cdn.domain.cdn.model.enums.CdnWarmStrategy;
import com.wewins.fota.cdn.spi.CdnWorkerConfigProvider;
import com.wewins.fota.cdn.spi.CdnWorkerConfigProvider.CdnWorkerConfig;
import com.wewins.fota.module.system.application.DictItemAppService;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdnWarmDictionaryConfigProvider implements CdnWorkerConfigProvider {

    static final String TYPE_CDN_WARM_WORKER_CONFIG = "cdn_warm.worker.config";
    private static final String VALUE_DEFAULT_CONFIG = "default";

    private final DictItemAppService dictItemAppService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CdnWorkerConfig getConfig() {
        CdnWorkerConfig cached = loadFromRedis();
        if (cached != null) {
            return cached;
        }

        CdnWorkerConfig resolved = loadConfigFromDictionary();
        saveToRedis(resolved);
        return resolved;
    }

    public void evictCache() {
        stringRedisTemplate.delete(RedisKeyConstants.CDN_WARM_WORKER_CONFIG_CACHE_KEY);
    }

    private CdnWorkerConfig loadConfigFromDictionary() {
        List<DictItem> items = dictItemAppService.listItemsByTypeCode(TYPE_CDN_WARM_WORKER_CONFIG).stream()
                .filter(this::isActive)
                .toList();

        if (!items.isEmpty()) {
            CdnWorkerConfig config = buildSingleItemConfig(items);
            log.info("Loaded CDN warm config from dictionary type={}", TYPE_CDN_WARM_WORKER_CONFIG);
            return config;
        }

        log.warn("CDN warm config not found in dictionary, fallback defaults will be used");
        return defaultConfig(CdnWarmStrategy.SERVER, null, null, null);
    }

    private CdnWorkerConfig loadFromRedis() {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyConstants.CDN_WARM_WORKER_CONFIG_CACHE_KEY);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, CdnWorkerConfig.class);
        } catch (Exception e) {
            log.warn("Failed to load CDN warm config from Redis cache: {}", e.getMessage());
            return null;
        }
    }

    private void saveToRedis(CdnWorkerConfig config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.CDN_WARM_WORKER_CONFIG_CACHE_KEY,
                    json,
                    RedisKeyConstants.CONFIG_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Failed to save CDN warm config to Redis cache: {}", e.getMessage());
        }
    }

    private CdnWorkerConfig buildSingleItemConfig(List<DictItem> items) {
        DictItem configItem = items.stream()
                .filter(item -> VALUE_DEFAULT_CONFIG.equalsIgnoreCase(item.getValue()))
                .findFirst()
                .orElse(items.getFirst());

        JsonNode extra = configItem.getExtra();
        return defaultConfig(
                readStrategy(extra),
                readText(extra, "workerUrl", "worker_url", "url"),
                readText(extra, "warmSecret", "warm_secret", "workerToken", "worker_token", "token"),
                readPositiveInt(extra, "defaultTtlSeconds", "default_ttl_seconds", "ttlSeconds", "ttl_seconds")
        );
    }

    private CdnWorkerConfig defaultConfig(
            CdnWarmStrategy strategy,
            String workerUrl,
            String warmSecret,
            Integer defaultTtlSeconds
    ) {
        return new CdnWorkerConfig(
                strategy == null ? CdnWarmStrategy.SERVER : strategy,
                normalizeText(workerUrl),
                normalizeText(warmSecret),
                defaultTtlSeconds != null && defaultTtlSeconds > 0 ? defaultTtlSeconds : getDefaultTtlFallback()
        );
    }

    private String readText(JsonNode source, String... fieldNames) {
        if (source == null || source.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = normalizeText(source.path(fieldName).asText(null));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer readPositiveInt(JsonNode source, String... fieldNames) {
        if (source == null || source.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = source.path(fieldName);
            if (valueNode.isInt() || valueNode.isLong() || valueNode.isTextual()) {
                int value = valueNode.asInt(0);
                if (value > 0) {
                    return value;
                }
            }
        }
        return null;
    }

    private CdnWarmStrategy readStrategy(JsonNode source) {
        if (source == null || source.isNull()) {
            return CdnWarmStrategy.SERVER;
        }
        String value = readText(source, "strategy", "warmStrategy", "mode");
        if (value == null) {
            return CdnWarmStrategy.SERVER;
        }
        try {
            return CdnWarmStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown CDN warm strategy '{}', fallback to SERVER", value);
            return CdnWarmStrategy.SERVER;
        }
    }

    private boolean isActive(DictItem item) {
        return item != null && "active".equalsIgnoreCase(item.getStatus());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int getDefaultTtlFallback() {
        return 86400 * 30;
    }
}
