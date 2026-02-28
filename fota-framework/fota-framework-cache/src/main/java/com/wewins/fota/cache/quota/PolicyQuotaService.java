package com.wewins.fota.cache.quota;

/**
 * 策略配额服务接口
 * <p>
 * 用于控制升级策略的每日配额，防止灰度发布时升级设备数超出预期。
 * 使用 Redis 计数器 + Lua 脚本保证原子性操作。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
public interface PolicyQuotaService {

    /**
     * 检查并递增配额计数
     * <p>
     * 原子操作：先检查当前配额是否已用尽，未用尽则递增计数器。
     * </p>
     *
     * @param policyId  策略 ID
     * @param maxQuota  最大配额数
     * @return true=配额可用且已占用，false=配额已用尽
     */
    boolean checkAndIncrementQuota(Long policyId, int maxQuota);

    /**
     * 获取当前配额使用量
     *
     * @param policyId 策略 ID
     * @return 当前已使用的配额数量
     */
    long getCurrentUsage(Long policyId);

    /**
     * 重置配额计数
     * <p>
     * 一般用于测试或异常恢复场景
     * </p>
     *
     * @param policyId 策略 ID
     */
    void resetQuota(Long policyId);
}
