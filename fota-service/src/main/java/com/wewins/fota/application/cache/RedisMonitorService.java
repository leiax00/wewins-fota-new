package com.wewins.fota.application.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.adapter.api.admin.dto.RedisInfoDTO;
import com.wewins.fota.adapter.api.admin.dto.RedisKeyDetailDTO;
import com.wewins.fota.adapter.api.admin.dto.RedisKeyListDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMonitorService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_COLLECTION_FETCH = 200;
    private static final int MAX_STRING_RANGE_BYTES = 32 * 1024;
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9:_\\-./]{1,256}$");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisInfoDTO getRedisInfo() {
        Properties info = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.info());
        if (info == null) {
            info = new Properties();
        }
        
        Map<String, String> rawInfo = new LinkedHashMap<>();
        info.forEach((k, v) -> rawInfo.put(String.valueOf(k), String.valueOf(v)));

        long usedMemory = parseLong(info.getProperty("used_memory", "0"));
        long usedMemoryPeak = parseLong(info.getProperty("used_memory_peak", "0"));
        long totalSystemMemory = parseLong(info.getProperty("total_system_memory", "0"));
        long keyspaceHits = parseLong(info.getProperty("keyspace_hits", "0"));
        long keyspaceMisses = parseLong(info.getProperty("keyspace_misses", "0"));
        
        double memoryUsagePercent = totalSystemMemory > 0 
            ? (usedMemory * 100.0 / totalSystemMemory) : 0;
        
        double hitRate = (keyspaceHits + keyspaceMisses) > 0 
            ? (keyspaceHits * 100.0 / (keyspaceHits + keyspaceMisses)) : 0;

        Map<String, Long> dbSizes = new LinkedHashMap<>();
        for (String key : info.stringPropertyNames()) {
            if (key.startsWith("db")) {
                String value = info.getProperty(key);
                dbSizes.put(key, parseDbSize(value));
            }
        }

        long totalKeys = dbSizes.values().stream().mapToLong(Long::longValue).sum();

        String mode = info.getProperty("redis_mode");
        if (mode == null || mode.isBlank()) {
            String clusterEnabled = info.getProperty("cluster_enabled", "0");
            if ("1".equals(clusterEnabled)) {
                mode = "cluster";
            } else {
                mode = "standalone";
            }
        }

        return RedisInfoDTO.builder()
                .version(info.getProperty("redis_version", "unknown"))
                .mode(mode)
                .connectedClients(parseLong(info.getProperty("connected_clients", "0")))
                .usedMemory(usedMemory)
                .usedMemoryPeak(usedMemoryPeak)
                .totalSystemMemory(totalSystemMemory)
                .memoryUsagePercent(Math.round(memoryUsagePercent * 100.0) / 100.0)
                .totalKeys(totalKeys)
                .expiredKeys(parseLong(info.getProperty("expired_keys", "0")))
                .evictedKeys(parseLong(info.getProperty("evicted_keys", "0")))
                .keyspaceHits(keyspaceHits)
                .keyspaceMisses(keyspaceMisses)
                .hitRate(Math.round(hitRate * 100.0) / 100.0)
                .totalCommandsProcessed(parseLong(info.getProperty("total_commands_processed", "0")))
                .instantaneousOpsPerSec(parseLong(info.getProperty("instantaneous_ops_per_sec", "0")))
                .uptimeInSeconds(parseLong(info.getProperty("uptime_in_seconds", "0")))
                .rdbLastSaveTime(parseLong(info.getProperty("rdb_last_save_time", "0")))
                .rdbLastStatus(info.getProperty("rdb_last_bgsave_status", "unknown"))
                .aofEnabled("1".equals(info.getProperty("aof_enabled", "0")))
                .dbSizes(dbSizes)
                .rawInfo(rawInfo)
                .build();
    }

    public RedisKeyListDTO listKeys(String pattern, String cursor, int pageSize) {
        String safePattern = normalizePattern(pattern);
        int safePageSize = normalizePageSize(pageSize);
        long offset = parseOffsetCursor(cursor);

        ScanOptions options = ScanOptions.scanOptions()
                .match(safePattern)
                .count(safePageSize)
                .build();

        List<RedisKeyListDTO.KeyItem> keys = new ArrayList<>();
        String nextCursor = null;

        try (Cursor<String> scanCursor = redisTemplate.scan(options)) {
            long skipped = 0;
            while (scanCursor.hasNext() && skipped < offset) {
                scanCursor.next();
                skipped++;
            }

            long consumed = 0;
            while (scanCursor.hasNext() && keys.size() < safePageSize) {
                keys.add(buildKeyItem(scanCursor.next()));
                consumed++;
            }

            if (scanCursor.hasNext()) {
                nextCursor = String.valueOf(offset + consumed);
            }
        } catch (Exception e) {
            log.warn("Failed to scan keys with pattern: {}, cursor: {}", safePattern, cursor, e);
        }

        return RedisKeyListDTO.builder()
                .keys(keys)
                .total((long) keys.size())
                .pageSize(safePageSize)
                .cursor(nextCursor)
                .build();
    }

    public RedisKeyDetailDTO getKeyDetail(String key) {
        validateKey(key);
        DataType type = redisTemplate.type(key);
        if (type == null || type == DataType.NONE) {
            return RedisKeyDetailDTO.builder()
                    .key(key)
                    .type("none")
                    .ttl(-2L)
                    .error("Key does not exist")
                    .build();
        }

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        Long memoryUsage = getMemoryUsage(key);
        Object value = getValue(key, type);
        Long length = getLength(key, type);

        return RedisKeyDetailDTO.builder()
                .key(key)
                .type(type.code())
                .ttl(ttl)
                .memoryUsage(memoryUsage)
                .encoding(getEncoding(type, key))
                .value(value)
                .length(length)
                .build();
    }

    public boolean setKeyValue(String key, String value, Long ttlSeconds) {
        validateKey(key);
        String safeValue = value == null ? "" : value;
        try {
            if (ttlSeconds != null && ttlSeconds > 0) {
                redisTemplate.opsForValue().set(key, safeValue, ttlSeconds, TimeUnit.SECONDS);
            } else if (ttlSeconds != null && ttlSeconds == -1) {
                redisTemplate.opsForValue().set(key, safeValue);
                redisTemplate.persist(key);
            } else {
                Long existingTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                redisTemplate.opsForValue().set(key, safeValue);
                if (existingTtl != null && existingTtl > 0) {
                    redisTemplate.expire(key, existingTtl, TimeUnit.SECONDS);
                }
            }
            auditLog("set-key", key, "ttl=" + ttlSeconds);
            return true;
        } catch (Exception e) {
            log.warn("Failed to set key: {}", key, e);
            return false;
        }
    }

    public boolean deleteKey(String key) {
        validateKey(key);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            auditLog("delete-key", key, "deleted=true");
        }
        return Boolean.TRUE.equals(deleted);
    }

    public boolean setTtl(String key, long ttlSeconds) {
        validateKey(key);
        if (ttlSeconds > 0) {
            boolean ok = Boolean.TRUE.equals(redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS));
            if (ok) {
                auditLog("set-ttl", key, "ttl=" + ttlSeconds);
            }
            return ok;
        } else if (ttlSeconds == -1) {
            boolean ok = Boolean.TRUE.equals(redisTemplate.persist(key));
            if (ok) {
                auditLog("set-ttl", key, "ttl=persist");
            }
            return ok;
        }
        return false;
    }

    private RedisKeyListDTO.KeyItem buildKeyItem(String key) {
        DataType type = redisTemplate.type(key);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        Long memoryUsage = getMemoryUsage(key);

        return RedisKeyListDTO.KeyItem.builder()
                .key(key)
                .type(type != null ? type.code() : "none")
                .ttl(ttl)
                .memoryUsage(memoryUsage)
                .build();
    }

    private Long getMemoryUsage(String key) {
        try {
            RedisCallback<Long> callback = connection -> {
                byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
                try {
                    Object raw = connection.execute("MEMORY", "USAGE".getBytes(StandardCharsets.UTF_8), rawKey);
                    if (raw instanceof Long value) {
                        return value;
                    }
                    if (raw instanceof byte[] bytes) {
                        return parseLong(new String(bytes, StandardCharsets.UTF_8));
                    }
                    return 0L;
                } catch (Exception e) {
                    return 0L;
                }
            };
            return redisTemplate.execute(callback);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String getEncoding(DataType type, String key) {
        try {
            RedisCallback<String> callback = connection -> {
                Object raw = connection.execute("OBJECT", "ENCODING".getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8));
                if (raw instanceof byte[] bytes) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                return null;
            };
            String encoding = redisTemplate.execute(callback);
            if (encoding != null && !encoding.isBlank()) {
                return encoding;
            }
        } catch (Exception ignored) {
        }
        return defaultEncoding(type);
    }

    private Object getValue(String key, DataType type) {
        try {
            return switch (type) {
                case STRING -> readStringValue(key);
                case LIST -> {
                    Long total = redisTemplate.opsForList().size(key);
                    long totalCount = total == null ? 0L : total;
                    List<String> rawItems = redisTemplate.opsForList().range(
                            key,
                            0,
                            totalCount > MAX_COLLECTION_FETCH ? MAX_COLLECTION_FETCH - 1 : -1
                    );
                    List<Object> items = normalizeList(rawItems);
                    yield collectionValue(totalCount, items);
                }
                case SET -> {
                    Long total = redisTemplate.opsForSet().size(key);
                    long totalCount = total == null ? 0L : total;
                    Object items;
                    if (totalCount <= MAX_COLLECTION_FETCH) {
                        var members = redisTemplate.opsForSet().members(key);
                        items = members == null ? List.of() : normalizeList(new ArrayList<>(members));
                    } else {
                        var preview = redisTemplate.opsForSet().distinctRandomMembers(key, MAX_COLLECTION_FETCH);
                        items = preview == null ? List.of() : normalizeList(new ArrayList<>(preview));
                    }
                    yield collectionValue(totalCount, items);
                }
                case ZSET -> {
                    Long total = redisTemplate.opsForZSet().size(key);
                    long totalCount = total == null ? 0L : total;
                    Map<String, Double> zsetValues = new LinkedHashMap<>();
                    var tuples = redisTemplate.opsForZSet().rangeWithScores(
                            key,
                            0,
                            totalCount > MAX_COLLECTION_FETCH ? MAX_COLLECTION_FETCH - 1 : -1
                    );
                    if (tuples != null) {
                        tuples.forEach(t -> zsetValues.put(String.valueOf(normalizeRedisPayload(t.getValue())), t.getScore()));
                    }
                    yield collectionValue(totalCount, zsetValues);
                }
                case HASH -> {
                    Long total = redisTemplate.opsForHash().size(key);
                    long totalCount = total == null ? 0L : total;
                    if (totalCount <= MAX_COLLECTION_FETCH) {
                        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                        yield entries == null ? Map.of() : normalizeMap(entries);
                    }

                    Map<Object, Object> limited = new LinkedHashMap<>();
                    try (Cursor<Map.Entry<Object, Object>> cursor = redisTemplate.opsForHash().scan(
                            key,
                            ScanOptions.scanOptions().count(MAX_COLLECTION_FETCH).build())) {
                        int i = 0;
                        while (cursor.hasNext() && i < MAX_COLLECTION_FETCH) {
                            Map.Entry<Object, Object> entry = cursor.next();
                            limited.put(entry.getKey(), entry.getValue());
                            i++;
                        }
                    }
                    yield collectionValue(totalCount, normalizeMap(limited));
                }
                case STREAM -> Map.of("info", redisTemplate.opsForStream().info(key));
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to get value for key: {}", key, e);
            return "Error reading value: " + e.getMessage();
        }
    }

    private Object readStringValue(String key) {
        return redisTemplate.execute((RedisCallback<Object>) connection -> {
            byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
            Long length = connection.stringCommands().strLen(rawKey);
            byte[] bytes = connection.stringCommands().getRange(rawKey, 0, MAX_STRING_RANGE_BYTES - 1L);
            String value = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
            if (length != null && length > MAX_STRING_RANGE_BYTES) {
                return Map.of(
                        "value", normalizeRedisPayload(value),
                        "truncated", true,
                        "totalLength", length,
                        "returnedLength", MAX_STRING_RANGE_BYTES
                );
            }
            return normalizeRedisPayload(value);
        });
    }

    private Object collectionValue(long totalCount, Object items) {
        if (totalCount <= MAX_COLLECTION_FETCH) {
            return items;
        }
        return Map.of(
                "items", items,
                "truncated", true,
                "total", totalCount,
                "limit", MAX_COLLECTION_FETCH
        );
    }

    private List<Object> normalizeList(List<?> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        List<Object> normalized = new ArrayList<>(rawItems.size());
        for (Object item : rawItems) {
            normalized.add(normalizeRedisPayload(item));
        }
        return normalized;
    }

    private Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            normalized.put(String.valueOf(normalizeRedisPayload(entry.getKey())), normalizeRedisPayload(entry.getValue()));
        }
        return normalized;
    }

    private Object normalizeRedisPayload(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return text;
        }
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String normalizePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "*";
        }
        if (pattern.length() > 256 || pattern.contains("\n") || pattern.contains("\r")) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "Invalid scan pattern");
        }
        return pattern;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private long parseOffsetCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            long offset = Long.parseLong(cursor);
            return Math.max(0L, offset);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "Invalid cursor");
        }
    }

    private void validateKey(String key) {
        if (!isSafeKey(key)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "Invalid redis key");
        }
    }

    private boolean isSafeKey(String key) {
        return key != null && SAFE_KEY_PATTERN.matcher(key).matches();
    }

    private String defaultEncoding(DataType type) {
        if (type == null) {
            return "none";
        }
        return switch (type) {
            case STRING -> "raw";
            case HASH, SET, ZSET -> "hashtable";
            case LIST -> "quicklist";
            default -> "unknown";
        };
    }

    private void auditLog(String action, String key, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String operator = authentication == null ? "anonymous" : authentication.getName();
        log.info("cache-audit action={} operator={} key={} detail={}", action, operator, key, detail);
    }

    private Long getLength(String key, DataType type) {
        try {
            return switch (type) {
                case STRING -> {
                    Long length = redisTemplate.execute((RedisCallback<Long>) connection ->
                            connection.stringCommands().strLen(key.getBytes(StandardCharsets.UTF_8)));
                    yield length == null ? 0L : length;
                }
                case LIST -> redisTemplate.opsForList().size(key);
                case SET -> redisTemplate.opsForSet().size(key);
                case ZSET -> redisTemplate.opsForZSet().size(key);
                case HASH -> redisTemplate.opsForHash().size(key);
                case STREAM -> redisTemplate.opsForStream().size(key);
                default -> 0L;
            };
        } catch (Exception e) {
            return 0L;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long parseDbSize(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            int equalsIndex = value.indexOf('=');
            int commaIndex = value.indexOf(',');
            if (equalsIndex > 0) {
                if (commaIndex > equalsIndex) {
                    return Long.parseLong(value.substring(equalsIndex + 1, commaIndex));
                } else {
                    return Long.parseLong(value.substring(equalsIndex + 1));
                }
            }
        } catch (NumberFormatException e) {
            return 0L;
        }
        return 0L;
    }
}
