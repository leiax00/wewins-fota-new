package com.wewins.fota.cache.quota;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务 Redis 实现
 * <p>
 * 基于 Redis 实现分布式锁，特性：
 * <ul>
 *   <li>使用 SET NX EX 命令实现原子性加锁</li>
 *   <li>使用 Lua 脚本保证原子性释放锁（仅持有者可释放）</li>
 *   <li>支持锁过期自动释放，防止死锁</li>
 *   <li>异常降级：Redis 不可用时操作失败（不会造成数据不一致）</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLockService implements DistributedLockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseLockScript;

    private static final long DEFAULT_EXPIRE_SECONDS = 30;

    @Override
    public LockResult tryLock(String lockName) {
        return tryLock(lockName, DEFAULT_EXPIRE_SECONDS);
    }

    @Override
    public LockResult tryLock(String lockName, long expireSeconds) {
        if (lockName == null || lockName.isEmpty()) {
            log.warn("锁名称为空");
            return LockResult.notAcquired();
        }

        try {
            String key = buildLockKey(lockName);
            String token = generateToken();

            // SET key value NX EX seconds
            // NX: 仅当 key 不存在时设置
            // EX: 设置过期时间（秒）
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, expireSeconds, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("获取锁成功: lockName={}, token={}", lockName, token);
                return LockResult.acquired(token);
            } else {
                log.debug("获取锁失败: lockName={}", lockName);
                return LockResult.notAcquired();
            }

        } catch (Exception e) {
            log.error("获取锁异常: lockName={}", lockName, e);
            return LockResult.notAcquired();
        }
    }

    @Override
    public boolean releaseLock(String lockName, String token) {
        if (lockName == null || lockName.isEmpty()) {
            log.warn("锁名称为空");
            return false;
        }

        if (token == null || token.isEmpty()) {
            log.warn("锁 token 为空");
            return false;
        }

        try {
            String key = buildLockKey(lockName);

            // 使用 Lua 脚本保证原子性：只释放自己持有的锁
            Long result = redisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(key),
                    token
            );

            boolean released = result == 1L;

            if (released) {
                log.debug("释放锁成功: lockName={}, token={}", lockName, token);
            } else {
                log.debug("释放锁失败（不是持有者或锁已过期）: lockName={}, token={}", lockName, token);
            }

            return released;

        } catch (Exception e) {
            log.error("释放锁异常: lockName={}, token={}", lockName, token, e);
            return false;
        }
    }

    @Override
    public boolean executeWithLock(String lockName, Runnable callback) {
        LockResult result = tryLock(lockName);

        if (!result.acquired()) {
            return false;
        }

        try {
            callback.run();
            return true;
        } finally {
            releaseLock(lockName, result.token());
        }
    }

    @Override
    public <T> Optional<T> executeWithLock(String lockName, Supplier<T> callback) {
        LockResult result = tryLock(lockName);

        if (!result.acquired()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(callback.get());
        } finally {
            releaseLock(lockName, result.token());
        }
    }

    /**
     * 构建锁 Redis Key
     * <p>
     * 注意：锁 Key 必须是固定的，不能包含时间戳等动态值，
     * 否则每次调用会生成不同的 Key，导致无法实现互斥锁。
     * </p>
     *
     * @param lockName 锁名称
     * @return Redis Key
     */
    private String buildLockKey(String lockName) {
        return String.format(RedisKeyConstants.LOCK_KEY_TEMPLATE, lockName);
    }

    /**
     * 生成唯一的锁 token
     * <p>
     * 使用 UUID 保证唯一性，确保只有锁的持有者才能释放锁
     * </p>
     *
     * @return 锁 token
     */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
