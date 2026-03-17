package com.wewins.fota.cache.quota;

import java.util.function.Supplier;

/**
 * 分布式锁服务接口
 * <p>
 * 提供基于 Redis 的分布式锁功能，支持：
 * <ul>
 *   <li>获取锁（带超时）</li>
 *   <li>释放锁（仅持有者可释放）</li>
 *   <li>执行回调（自动释放锁）</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
public interface DistributedLockService {

    /**
     * 尝试获取锁
     *
     * @param lockName 锁名称
     * @return LockResult 包含锁 token，用于后续释放
     */
    LockResult tryLock(String lockName);

    /**
     * 尝试获取锁（指定超时时间）
     *
     * @param lockName    锁名称
     * @param expireSeconds 锁过期时间（秒）
     * @return LockResult 包含锁 token，用于后续释放
     */
    LockResult tryLock(String lockName, long expireSeconds);

    /**
     * 释放锁
     * <p>
     * 只有锁的持有者才能成功释放
     * </p>
     *
     * @param lockName 锁名称
     * @param token    锁 token
     * @return true=释放成功，false=不是锁持有者或锁已过期
     */
    boolean releaseLock(String lockName, String token);

    /**
     * 执行回调（自动获取和释放锁）
     * <p>
     * 如果获取锁失败，回调不会执行
     * </p>
     *
     * @param lockName 锁名称
     * @param callback 回调函数
     * @return true=执行成功，false=获取锁失败
     */
    boolean executeWithLock(String lockName, Runnable callback);

    /**
     * 执行回调（自动获取和释放锁，带返回值）
     *
     * @param lockName 锁名称
     * @param callback 回调函数
     * @param <T>      返回值类型
     * @return 回调结果，如果获取锁失败返回 empty
     */
    <T> java.util.Optional<T> executeWithLock(String lockName, Supplier<T> callback);

    /**
     * 锁获取结果
     */
    record LockResult(
            /**
             * 是否成功获取锁
             */
            boolean acquired,
            /**
             * 锁 token（用于释放锁）
             */
            String token
    ) {
        /**
         * 创建成功结果
         */
        public static LockResult acquired(String token) {
            return new LockResult(true, token);
        }

        /**
         * 创建失败结果
         */
        public static LockResult notAcquired() {
            return new LockResult(false, null);
        }
    }
}
