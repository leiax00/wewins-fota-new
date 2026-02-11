package com.wewins.fota.cache.constant;

/**
 * Redis Key 常量定义
 * <p>
 * 统一管理 Redis Key 的命名规范和前缀
 * </p>
 * <p>
 * 命名规范：fota:{module}:{key}
 * </p>
 * <p>
 * 示例：
 * <ul>
 *   <li>fota:device:861234567890123 - 设备信息缓存</li>
 *   <li>fota:policy:101 - 策略缓存</li>
 *   <li>fota:product:1001 - 产品信息缓存</li>
 *   <li>fota:config:cn - 配置快照</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
        // 防止实例化
    }

    /**
     * 设备信息 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei)
     * </p>
     * <p>
     * 示例：fota:device:861234567890123
     * </p>
     */
    public static final String DEVICE_KEY_TEMPLATE = "fota:device:%s";

    /**
     * 策略信息 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_KEY_TEMPLATE, policyId)
     * </p>
     * <p>
     * 示例：fota:policy:101
     * </p>
     */
    public static final String POLICY_KEY_TEMPLATE = "fota:policy:%s";

    /**
     * 产品信息 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.PRODUCT_KEY_TEMPLATE, productId)
     * </p>
     * <p>
     * 示例：fota:product:1001
     * </p>
     */
    public static final String PRODUCT_KEY_TEMPLATE = "fota:product:%s";

    /**
     * 配置信息 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.CONFIG_KEY_TEMPLATE, region)
     * </p>
     * <p>
     * 示例：fota:config:cn
     * </p>
     */
    public static final String CONFIG_KEY_TEMPLATE = "fota:config:%s";

    /**
     * 分布式锁 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.LOCK_KEY_TEMPLATE, lockName, lockValue)
     * </p>
     * <p>
     * 示例：fota:lock:device_import:batch_123
     * </p>
     */
    public static final String LOCK_KEY_TEMPLATE = "fota:lock:%s:%s";

    /**
     * 限流 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.RATE_LIMIT_KEY_TEMPLATE, limitName, limitKey)
     * </p>
     * <p>
     * 示例：fota:ratelimit:upgrade_check:127.0.0.1
     * </p>
     */
    public static final String RATE_LIMIT_KEY_TEMPLATE = "fota:ratelimit:%s:%s";

    // ========== TTL 常量 ==========

    /**
     * 设备信息缓存 TTL（24 小时）
     */
    public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60;

    /**
     * 策略信息缓存 TTL（1 小时）
     */
    public static final long POLICY_CACHE_TTL_SECONDS = 60 * 60;

    /**
     * 产品信息缓存 TTL（1 小时）
     */
    public static final long PRODUCT_CACHE_TTL_SECONDS = 60 * 60;

    /**
     * 配置信息缓存 TTL（6 小时）
     */
    public static final long CONFIG_CACHE_TTL_SECONDS = 6 * 60 * 60;

    /**
     * 分布式锁 TTL（30 秒）
     */
    public static final long LOCK_TTL_SECONDS = 30;

    /**
     * 限流窗口 TTL（60 秒）
     */
    public static final long RATE_LIMIT_TTL_SECONDS = 60;
}
