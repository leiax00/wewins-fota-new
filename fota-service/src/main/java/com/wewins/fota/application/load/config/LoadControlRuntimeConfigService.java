package com.wewins.fota.application.load.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadControlRuntimeConfigService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final AtomicReference<LoadControlRuntimeConfig> cachedConfig = new AtomicReference<>();
    private final AtomicReference<Instant> lastCacheAt = new AtomicReference<>(Instant.EPOCH);

    public Optional<LoadControlRuntimeConfig> getPublishedConfig() {
        Instant now = Instant.now();
        if (Duration.between(lastCacheAt.get(), now).compareTo(CACHE_TTL) < 0) {
            return Optional.ofNullable(cachedConfig.get());
        }

        String json = redisTemplate.opsForValue().get(RedisKeyConstants.LOAD_CONTROL_ACTIVE_CONFIG_KEY);
        if (json == null || json.isBlank()) {
            cachedConfig.set(null);
            lastCacheAt.set(now);
            return Optional.empty();
        }

        try {
            LoadControlRuntimeConfig config = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(json, LoadControlRuntimeConfig.class);
            cachedConfig.set(config);
            lastCacheAt.set(now);
            return Optional.of(config);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize published load-control config", e);
            cachedConfig.set(null);
            lastCacheAt.set(now);
            return Optional.empty();
        }
    }

    public Map<String, LoadScoringMetricConfig> getEffectiveScoringMetricMap() {
        Map<String, LoadScoringMetricConfig> merged = new LinkedHashMap<>();
        LoadScoringConfig defaults = LoadControlDefaults.defaultScoringConfig();
        addMetrics(merged, defaults);
        getPublishedConfig()
                .map(LoadControlRuntimeConfig::getScoring)
                .ifPresent(scoring -> addMetrics(merged, scoring));
        return merged;
    }

    public ControlParameter getEffectiveGlobalControlParameter() {
        return getPublishedConfig()
                .map(LoadControlRuntimeConfig::getControl)
                .orElseGet(ControlParameter::createGlobalDefault);
    }

    public LoadControlRuntimeConfig updatePublishedControlParameter(ControlParameter controlParameter) {
        ControlParameter effective = controlParameter == null ? ControlParameter.createGlobalDefault() : controlParameter;
        Instant updatedAt = effective.getUpdatedAt() != null ? effective.getUpdatedAt() : Instant.now();

        LoadControlRuntimeConfig base = getPublishedConfig().orElseGet(this::defaultRuntimeConfig);
        LoadControlRuntimeConfig updated = LoadControlRuntimeConfig.builder()
                .version(nextVersion())
                .updatedAt(updatedAt)
                .updatedBy(effective.getUpdatedBy())
                .control(effective)
                .scoring(base.getScoring() != null ? base.getScoring() : LoadControlDefaults.defaultScoringConfig())
                .sentinel(base.getSentinel() != null ? base.getSentinel() : emptySentinelRules())
                .build();
        publish(updated);
        return updated;
    }

    public long nextVersion() {
        Long next = redisTemplate.opsForValue().increment(RedisKeyConstants.LOAD_CONTROL_CONFIG_VERSION_KEY);
        return next == null ? 1L : next;
    }

    public void publish(LoadControlRuntimeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Load-control runtime config must not be null");
        }
        if (config.getVersion() == null) {
            config.setVersion(nextVersion());
        }
        if (config.getUpdatedAt() == null) {
            config.setUpdatedAt(Instant.now());
        }
        try {
            String json = objectMapper.writeValueAsString(config);
            redisTemplate.opsForValue().set(RedisKeyConstants.LOAD_CONTROL_ACTIVE_CONFIG_KEY, json);
            redisTemplate.opsForValue().set(RedisKeyConstants.LOAD_CONTROL_LAST_PUBLISH_AT_KEY, config.getUpdatedAt().toString());
            cachedConfig.set(config);
            lastCacheAt.set(Instant.now());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize published load-control config", e);
        }
    }

    public void evictLocalCache() {
        cachedConfig.set(null);
        lastCacheAt.set(Instant.EPOCH);
    }

    private LoadControlRuntimeConfig defaultRuntimeConfig() {
        return LoadControlRuntimeConfig.builder()
                .control(ControlParameter.createGlobalDefault())
                .scoring(LoadControlDefaults.defaultScoringConfig())
                .sentinel(emptySentinelRules())
                .build();
    }

    private SentinelRulesDTO emptySentinelRules() {
        return SentinelRulesDTO.builder()
                .flowRules(List.of())
                .degradeRules(List.of())
                .build();
    }

    private void addMetrics(Map<String, LoadScoringMetricConfig> target, LoadScoringConfig scoring) {
        if (scoring == null) {
            return;
        }
        addMetrics(target, scoring.getInstanceMetrics());
        addMetrics(target, scoring.getHostMetrics());
        addMetrics(target, scoring.getRegionMetrics());
    }

    private void addMetrics(Map<String, LoadScoringMetricConfig> target, Iterable<LoadScoringMetricConfig> metrics) {
        if (metrics == null) {
            return;
        }
        for (LoadScoringMetricConfig metric : metrics) {
            if (metric != null && metric.getMetricKey() != null && !metric.getMetricKey().isBlank()) {
                target.put(metric.getMetricKey(), metric);
            }
        }
    }
}
