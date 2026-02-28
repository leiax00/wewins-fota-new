package com.wewins.fota.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.policy.snapshot.PolicySnapshot;
import com.wewins.fota.domain.policy.snapshot.PolicySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 策略快照仓储的 Redis 实现
 * <p>
 * 基于 Redis + Lua 脚本实现原子性快照切换：
 * <ul>
 *   <li>使用 Hash 存储快照内容</li>
 *   <li>使用 String 存储版本指针</li>
 *   <li>Lua 脚本保证写入和切换的原子性</li>
 *   <li>支持降级到数据库</li>
 * </ul>
 * </p>
 *
 * <h3>Redis 键设计</h3>
 * <pre>
 * fota:pol:active_ver:{productId}  -> 版本号（String）
 * fota:pol:snap:{productId}:v{ver} -> 快照内容（Hash）
 * fota:pol:sync_ts:{productId}     -> 同步时间戳（String）
 * </pre>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisPolicySnapshotRepository implements PolicySnapshotRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 快照 TTL（7 天）
     */
    private static final long SNAPSHOT_TTL_SECONDS = RedisKeyConstants.POLICY_SNAPSHOT_TTL_SECONDS;

    /**
     * 写入并切换快照的 Lua 脚本
     * <p>
     * 保证原子性：
     * <ol>
     *   <li>写入快照内容（Hash）</li>
     *   <li>更新同步时间戳</li>
     *   <li>切换版本指针</li>
     *   <li>将版本号添加到版本列表（SET）</li>
     *   <li>清理旧版本（保留最近 3 个）</li>
     * </ol>
     * </p>
     * <p>
     * 注意：使用 SET 维护版本列表，避免使用 KEYS 命令导致 Redis 阻塞
     * </p>
     */
    private static final String WRITE_AND_SWITCH_SCRIPT = """
            local snapKey = KEYS[1]           -- fota:pol:snap:{productId}:v{ver}
            local verKey = KEYS[2]            -- fota:pol:active_ver:{productId}
            local tsKey = KEYS[3]             -- fota:pol:sync_ts:{productId}
            local verListKey = KEYS[4]        -- fota:pol:versions:{productId} (SET)
            local timestamp = ARGV[1]         -- 当前时间戳
            local version = ARGV[2]           -- 新版本号
            local ttl = ARGV[3]               -- 快照 TTL
            local keepCount = tonumber(ARGV[4]) -- 保留版本数

            -- 写入快照字段（从 ARGV[5] 开始是键值对）
            for i = 5, #ARGV, 2 do
                redis.call('HSET', snapKey, ARGV[i], ARGV[i + 1])
            end

            -- 设置快照过期时间
            redis.call('EXPIRE', snapKey, ttl)

            -- 更新同步时间戳
            redis.call('SET', tsKey, timestamp)
            redis.call('EXPIRE', tsKey, 2592000)  -- 30 天

            -- 切换版本指针
            redis.call('SET', verKey, version)

            -- 将版本号添加到版本列表（使用 SET 存储）
            redis.call('SADD', verListKey, version)
            redis.call('EXPIRE', verListKey, 2592000)  -- 30 天

            -- 从版本列表获取所有版本并清理旧版本
            local verList = redis.call('SMEMBERS', verListKey)
            local numVerList = {}

            for _, v in ipairs(verList) do
                local verNum = tonumber(v)
                if verNum then
                    table.insert(numVerList, verNum)
                end
            end

            table.sort(numVerList, function(a, b) return a > b end)

            -- 删除超出保留数量的旧版本
            for i = keepCount + 1, #numVerList do
                local oldVer = numVerList[i]
                local oldKey = string.gsub(snapKey, 'v' .. version, 'v' .. oldVer)
                redis.call('DEL', oldKey)
                redis.call('SREM', verListKey, tostring(oldVer))
            end

            return 1  -- 成功
            """;

    /**
     * 获取 Lua 脚本实例
     */
    private DefaultRedisScript<Long> writeAndSwitchScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(WRITE_AND_SWITCH_SCRIPT);
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public boolean writeSnapshot(PolicySnapshot snapshot) {
        if (snapshot == null || snapshot.getProductId() == null) {
            log.warn("快照或产品 ID 为空，写入失败");
            return false;
        }

        try {
            String snapKey = buildSnapshotKey(snapshot.getProductId(), snapshot.getVersion());
            Map<String, String> data = serializeSnapshot(snapshot);

            // 使用 Pipeline 批量写入
            redisTemplate.opsForHash().putAll(snapKey, data);
            redisTemplate.expire(snapKey, Duration.ofSeconds(SNAPSHOT_TTL_SECONDS));

            log.debug("策略快照已写入: productId={}, version={}",
                    snapshot.getProductId(), snapshot.getVersion());
            return true;

        } catch (Exception e) {
            log.error("写入策略快照失败: productId={}", snapshot.getProductId(), e);
            return false;
        }
    }

    @Override
    public boolean switchVersion(Long productId, String newVersion) {
        if (productId == null || newVersion == null) {
            log.warn("产品 ID 或版本号为空，切换失败");
            return false;
        }

        try {
            String verKey = buildActiveVersionKey(productId);
            redisTemplate.opsForValue().set(verKey, newVersion);

            log.debug("版本指针已切换: productId={}, version={}", productId, newVersion);
            return true;

        } catch (Exception e) {
            log.error("切换版本指针失败: productId={}, version={}", productId, newVersion, e);
            return false;
        }
    }

    @Override
    public boolean writeAndSwitch(PolicySnapshot snapshot) {
        if (snapshot == null || snapshot.getProductId() == null) {
            log.warn("快照或产品 ID 为空，写入并切换失败");
            return false;
        }

        try {
            String snapKey = buildSnapshotKey(snapshot.getProductId(), snapshot.getVersion());
            String verKey = buildActiveVersionKey(snapshot.getProductId());
            String tsKey = buildSyncTimestampKey(snapshot.getProductId());
            String verListKey = buildVersionListKey(snapshot.getProductId());

            // 准备 Lua 脚本参数
            List<String> keys = List.of(snapKey, verKey, tsKey, verListKey);
            List<String> args = new ArrayList<>();

            // ARGV[1] = 当前时间戳
            args.add(String.valueOf(Instant.now().getEpochSecond()));
            // ARGV[2] = 版本号
            args.add(snapshot.getVersion());
            // ARGV[3] = TTL
            args.add(String.valueOf(SNAPSHOT_TTL_SECONDS));
            // ARGV[4] = 保留版本数
            args.add("3");

            // ARGV[5+] = 快照内容（键值对）
            Map<String, String> data = serializeSnapshot(snapshot);
            for (Map.Entry<String, String> entry : data.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }

            // 执行 Lua 脚本
            Long result = redisTemplate.execute(writeAndSwitchScript(), keys, args);

            boolean success = result == 1L;
            if (success) {
                log.info("策略快照写入并切换成功: productId={}, version={}",
                        snapshot.getProductId(), snapshot.getVersion());
            } else {
                log.warn("策略快照写入并切换失败: productId={}, version={}, result={}",
                        snapshot.getProductId(), snapshot.getVersion(), result);
            }

            return success;

        } catch (Exception e) {
            log.error("写入并切换策略快照异常: productId={}", snapshot.getProductId(), e);
            return false;
        }
    }

    @Override
    public Optional<PolicySnapshot> loadSnapshot(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }

        try {
            // 获取当前版本号
            String version = getActiveVersion(productId);
            if (version == null) {
                log.debug("未找到活跃版本号: productId={}", productId);
                return Optional.empty();
            }

            return loadSnapshotByVersion(productId, version);

        } catch (Exception e) {
            log.error("加载策略快照失败: productId={}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PolicySnapshot> loadSnapshotByVersion(Long productId, String version) {
        if (productId == null || version == null) {
            return Optional.empty();
        }

        try {
            String snapKey = buildSnapshotKey(productId, version);
            Map<Object, Object> rawMap = redisTemplate.opsForHash().entries(snapKey);

            if (rawMap.isEmpty()) {
                log.debug("快照内容为空: productId={}, version={}", productId, version);
                return Optional.empty();
            }

            // 转换为 String Map
            Map<String, String> data = rawMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> String.valueOf(e.getValue())
                    ));

            PolicySnapshot snapshot = deserializeSnapshot(productId, version, data);
            return Optional.of(snapshot);

        } catch (Exception e) {
            log.error("加载指定版本策略快照失败: productId={}, version={}", productId, version, e);
            return Optional.empty();
        }
    }

    @Override
    public String getActiveVersion(Long productId) {
        if (productId == null) {
            return null;
        }

        try {
            String verKey = buildActiveVersionKey(productId);
            return redisTemplate.opsForValue().get(verKey);

        } catch (Exception e) {
            log.error("获取活跃版本号失败: productId={}", productId, e);
            return null;
        }
    }

    @Override
    public Long getLastSyncTime(Long productId) {
        if (productId == null) {
            return null;
        }

        try {
            String tsKey = buildSyncTimestampKey(productId);
            String value = redisTemplate.opsForValue().get(tsKey);

            if (value != null) {
                return Long.parseLong(value);
            }
            return null;

        } catch (NumberFormatException e) {
            log.warn("同步时间戳格式错误: productId={}", productId, e);
            return null;
        } catch (Exception e) {
            log.error("获取同步时间戳失败: productId={}", productId, e);
            return null;
        }
    }

    @Override
    public boolean isSyncStale(Long productId, long staleAgeSeconds) {
        Long lastSync = getLastSyncTime(productId);
        if (lastSync == null) {
            return true;
        }

        long age = Instant.now().getEpochSecond() - lastSync;
        return age > staleAgeSeconds;
    }

    @Override
    public int cleanupOldVersions(Long productId, int keepVersions) {
        if (productId == null) {
            return 0;
        }

        try {
            String pattern = buildSnapshotPattern(productId);
            // 使用 SCAN 替代 KEYS 命令，避免阻塞 Redis
            List<String> keys = scanKeys(pattern);

            if (keys.isEmpty()) {
                return 0;
            }

            // 提取版本号并排序
            List<VersionKey> versionKeys = new ArrayList<>();
            for (String key : keys) {
                String verStr = extractVersionFromKey(key);
                if (verStr != null) {
                    try {
                        long ver = Long.parseLong(verStr);
                        versionKeys.add(new VersionKey(key, ver));
                    } catch (NumberFormatException ignored) {
                        // 忽略非数字版本号
                    }
                }
            }

            // 按版本号降序排序
            versionKeys.sort((a, b) -> Long.compare(b.version(), a.version()));

            // 删除超出保留数量的版本
            int deleted = 0;
            for (int i = keepVersions; i < versionKeys.size(); i++) {
                Boolean result = redisTemplate.delete(versionKeys.get(i).key());
                if (result) {
                    deleted++;
                }
            }

            if (deleted > 0) {
                log.info("清理旧版本快照: productId={}, deleted={}", productId, deleted);
            }

            return deleted;

        } catch (Exception e) {
            log.error("清理旧版本快照失败: productId={}", productId, e);
            return 0;
        }
    }

    @Override
    public boolean deleteAllSnapshots(Long productId) {
        if (productId == null) {
            return false;
        }

        try {
            String pattern = buildSnapshotPattern(productId);
            // 使用 SCAN 替代 KEYS 命令，避免阻塞 Redis
            List<String> keys = scanKeys(pattern);

            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            // 删除版本指针、同步时间戳和版本列表
            redisTemplate.delete(buildActiveVersionKey(productId));
            redisTemplate.delete(buildSyncTimestampKey(productId));
            redisTemplate.delete(buildVersionListKey(productId));

            log.info("已删除所有快照: productId={}", productId);
            return true;

        } catch (Exception e) {
            log.error("删除所有快照失败: productId={}", productId, e);
            return false;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 序列化快照为 Redis Hash 数据
     */
    private Map<String, String> serializeSnapshot(PolicySnapshot snapshot) {
        try {
            Map<String, String> map = new java.util.LinkedHashMap<>();

            // 元数据
            map.put("ver", snapshot.getVersion());
            map.put("product_id", String.valueOf(snapshot.getProductId()));
            map.put("generated_at", String.valueOf(snapshot.getGeneratedAt()));
            map.put("generated_by", snapshot.getGeneratedBy());

            // JSON 字段
            map.put("policies", objectMapper.writeValueAsString(snapshot.getPolicies()));
            map.put("quota", objectMapper.writeValueAsString(snapshot.getQuota()));
            map.put("firmwares", objectMapper.writeValueAsString(snapshot.getFirmwares()));
            map.put("control", objectMapper.writeValueAsString(snapshot.getControl()));

            return map;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化快照失败", e);
        }
    }

    /**
     * 反序列化 Redis Hash 数据为快照
     */
    private PolicySnapshot deserializeSnapshot(Long productId, String version,
                                               Map<String, String> data) {
        try {
            return PolicySnapshot.builder()
                    .version(version)
                    .productId(productId)
                    .generatedAt(Long.parseLong(data.getOrDefault("generated_at", "0")))
                    .generatedBy(data.getOrDefault("generated_by", "unknown"))
                    .policies(objectMapper.readValue(
                            data.get("policies"),
                            objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, PolicySnapshot.PolicySelector.class)))
                    .quota(objectMapper.readValue(
                            data.get("quota"),
                            PolicySnapshot.QuotaConfig.class))
                    .firmwares(objectMapper.readValue(
                            data.get("firmwares"),
                            objectMapper.getTypeFactory().constructMapType(
                                    Map.class, String.class, PolicySnapshot.FirmwareMetadata.class)))
                    .control(objectMapper.readValue(
                            data.get("control"),
                            PolicySnapshot.ControlConfig.class))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("反序列化快照失败", e);
        }
    }

    /**
     * 构建快照键
     */
    private String buildSnapshotKey(Long productId, String version) {
        return String.format(RedisKeyConstants.POLICY_SNAPSHOT_KEY_TEMPLATE, productId, version);
    }

    /**
     * 构建快照键模式（用于扫描）
     */
    private String buildSnapshotPattern(Long productId) {
        return String.format(RedisKeyConstants.POLICY_SNAPSHOT_KEY_TEMPLATE, productId, "*");
    }

    /**
     * 构建活跃版本键
     */
    private String buildActiveVersionKey(Long productId) {
        return String.format(RedisKeyConstants.POLICY_ACTIVE_VER_KEY_TEMPLATE, productId);
    }

    /**
     * 构建同步时间戳键
     */
    private String buildSyncTimestampKey(Long productId) {
        return String.format(RedisKeyConstants.POLICY_SYNC_TS_KEY_TEMPLATE, productId);
    }

    /**
     * 构建版本列表键（SET）
     * <p>
     * 用于存储产品的所有快照版本号，替代 KEYS 命令扫描
     * </p>
     */
    private String buildVersionListKey(Long productId) {
        return "fota:pol:versions:" + productId;
    }

    /**
     * 从键中提取版本号
     */
    private String extractVersionFromKey(String key) {
        int idx = key.lastIndexOf(":v");
        if (idx > 0) {
            return key.substring(idx + 2);
        }
        return null;
    }

    /**
     * 使用 SCAN 命令扫描匹配的 Key
     * <p>
     * 替代 KEYS 命令，避免在大规模数据时阻塞 Redis
     * </p>
     *
     * @param pattern Key 匹配模式
     * @return 匹配的 Key 列表
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (var cursor = redisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build())) {
            cursor.forEachRemaining(key -> keys.add((String) key));
        }
        return keys;
    }

    /**
     * 版本键记录
     */
    private record VersionKey(String key, long version) {
    }
}
