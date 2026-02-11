package com.wewins.fota.infra.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务接口
 * <p>
 * 提供分布式环境下的互斥锁能力，用于协调多实例并发操作
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 *   <li>配额扣减 - 确保同一策略的配额不会被重复扣减</li>
 *   <li>灰度分桶写入 - 避免重复创建分桶记录</li>
 *   <li>设备版本更新 - 确保同一设备的版本更新操作串行化</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public interface DistributedLockService {

    /**
     * 尝试获取锁
     * <p>
     * 如果锁已被其他实例持有，则返回 false
     * </p>
     *
     * @param key    锁的键名
     * @param leaseTime 锁的租约时间
     * @param unit    租约时间单位
     * @return true 如果成功获取锁，false 如果锁已被持有
     */
    boolean tryLock(String key, long leaseTime, TimeUnit unit);

    /**
     * 释放锁
     * <p>
     * 只有持有锁的实例才能释放锁
     * </p>
     *
     * @param key 锁的键名
     */
    void unlock(String key);

    /**
     * 尝试获取锁并执行操作
     * <p>
     * 在持有锁期间执行指定的操作，操作完成后自动释放锁
     * </p>
     *
     * @param key    锁的键名
     * @param leaseTime 锁的租约时间
     * @param unit    租约时间单位
     * @param action  要执行的操作
     * @return 操作的返回值，如果获取锁失败则返回 null
     */
    <T> T executeWithLock(String key, long leaseTime, TimeUnit unit, java.util.function.Supplier<T> action);
}
