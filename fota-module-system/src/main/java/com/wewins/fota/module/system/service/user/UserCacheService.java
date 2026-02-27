package com.wewins.fota.module.system.service.user;

import com.wewins.fota.module.system.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户缓存服务
 * <p>
 * 使用 Redis 缓存用户ID到用户名的映射，减少数据库查询
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final String USER_NAME_CACHE_KEY_PREFIX = "fota:user:name:";
    private static final Duration CACHE_TTL = Duration.ofHours(2); // 缓存2小时

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    /**
     * 批量获取用户名称（优先从缓存读取）
     *
     * @param userIds 用户ID集合
     * @return 用户ID到用户名的映射
     */
    public Map<Long, String> getUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> result = new HashMap<>();
        Set<Long> uncachedIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        if (uncachedIds.isEmpty()) {
            return result;
        }

        // 1. 批量从 Redis 获取已缓存的用户名
        var cacheKeys = uncachedIds.stream()
                .map(id -> USER_NAME_CACHE_KEY_PREFIX + id)
                .toList();

        var cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys);
        if (cachedValues != null) {
            Iterator<Long> idIter = uncachedIds.iterator();
            Iterator<String> valueIter = cachedValues.iterator();
            while (idIter.hasNext() && valueIter.hasNext()) {
                Long userId = idIter.next();
                String cachedName = valueIter.next();
                if (cachedName != null) {
                    result.put(userId, cachedName);
                }
            }
        }

        // 2. 查询未缓存的用户
        Set<Long> missedIds = uncachedIds.stream()
                .filter(id -> !result.containsKey(id))
                .collect(Collectors.toSet());

        if (!missedIds.isEmpty()) {
            try {
                Map<Long, String> dbUsers = userRepository.findNameByIds(missedIds);
                result.putAll(dbUsers);

                // 3. 将查询结果写入缓存（使用 pipeline 批量写入）
                redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    for (Map.Entry<Long, String> entry : dbUsers.entrySet()) {
                        String key = USER_NAME_CACHE_KEY_PREFIX + entry.getKey();
                        byte[] keyBytes = redisTemplate.getStringSerializer().serialize(key);
                        byte[] valueBytes = redisTemplate.getStringSerializer().serialize(entry.getValue());
                        if (keyBytes != null && valueBytes != null) {
                            connection.stringCommands().setEx(keyBytes, CACHE_TTL.getSeconds(), valueBytes);
                        }
                    }
                    return null;
                });

                if (log.isDebugEnabled() && !dbUsers.isEmpty()) {
                    log.debug("缓存用户名称: count={}, userIds={}", dbUsers.size(), dbUsers.keySet());
                }
            } catch (Exception e) {
                log.warn("批量查询用户名称失败: userIds={}, error={}", missedIds, e.getMessage());
                // 即使查询失败，也返回已缓存的部分数据
            }
        }

        return result;
    }

    /**
     * 获取单个用户名称
     *
     * @param userId 用户ID
     * @return 用户名，如果不存在返回 null
     */
    public String getUserName(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }

        String cacheKey = USER_NAME_CACHE_KEY_PREFIX + userId;
        String cachedName = redisTemplate.opsForValue().get(cacheKey);

        if (cachedName != null) {
            return cachedName;
        }

        // 从数据库查询并缓存
        String userName = userRepository.findNameById(userId);
        if (userName != null) {
            redisTemplate.opsForValue().set(cacheKey, userName, CACHE_TTL);
        }

        return userName;
    }

    /**
     * 清除用户缓存
     *
     * @param userId 用户ID
     */
    public void evictUser(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        String cacheKey = USER_NAME_CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(cacheKey);
        if (log.isDebugEnabled()) {
            log.debug("清除用户缓存: userId={}", userId);
        }
    }
}
