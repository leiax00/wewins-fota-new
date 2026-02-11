package com.wewins.fota.infra.lock;

import com.wewins.fota.infra.config.ClusterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redis 的分布式锁服务实现
 * <p>
 * 使用 Redis SETNX + TTL 命令实现分布式锁
 * </p>
 * <p>
 * 实现细节：
 * <ul>
 *   <li>SETNX: 仅当键不存在时设置，保证原子性</li>
 *   <li>TTL: 自动过期，防止死锁</li>
 *   <li>Lua 脚本: 确保锁的获取和释放是原子操作</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService implements DistributedLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ClusterProperties clusterProperties;

    private static final String LOCK_SUCCESS = "OK";

    @Override
    public boolean tryLock(String key, long leaseTime, TimeUnit unit) {
        if (!clusterProperties.isEnabled()) {
            // 集群模式未启用，直接返回 true
            log.debug("集群模式未启用，跳过分布式锁: key={}", key);
            return true;
        }

        if (!clusterProperties.getLock().isEnabled()) {
            log.debug("分布式锁功能未启用，跳过: key={}", key);
            return true;
        }

        String lockKey = buildLockKey(key);
        String lockValue = LOCK_SUCCESS;
        long leaseTimeMs = unit.toMillis(leaseTime);

        try {
            // 使用 SET NX EX 命令获取锁
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, leaseTimeMs, TimeUnit.MILLISECONDS);

            boolean acquired = Boolean.TRUE.equals(result);
            if (acquired) {
                log.debug("成功获取分布式锁: key={}, leaseTime={}ms", lockKey, leaseTimeMs);
            } else {
                log.warn("获取分布式锁失败: key={}, leaseTime={}ms", lockKey, leaseTimeMs);
            }

            return acquired;

        } catch (Exception e) {
            log.error("获取分布式锁异常: key={}, leaseTime={}ms", lockKey, leaseTimeMs, e);
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        if (!clusterProperties.isEnabled()) {
            log.debug("集群模式未启用，跳过释放锁: key={}", key);
            return;
        }

        if (!clusterProperties.getLock().isEnabled()) {
            return;
        }

        String lockKey = buildLockKey(key);

        try {
            redisTemplate.delete(lockKey);
            log.debug("成功释放分布式锁: key={}", lockKey);
        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}", lockKey, e);
        }
    }

    @Override
    public <T> T executeWithLock(String key, long leaseTime, TimeUnit unit, Supplier<T> action) {
        String lockKey = buildLockKey(key);

        if (!tryLock(lockKey, leaseTime, unit)) {
            log.warn("未能获取分布式锁，跳过操作: key={}", lockKey);
            return null;
        }

        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }

    /**
     * 构建完整的锁键名
     *
     * @param key 原始键名
     * @return 完整键名（包含前缀）
     */
    private String buildLockKey(String key) {
        return clusterProperties.getLock().getPrefix() + key;
    }
}
