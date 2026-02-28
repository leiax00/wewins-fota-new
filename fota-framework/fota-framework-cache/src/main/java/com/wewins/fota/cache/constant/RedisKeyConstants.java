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

    /**
     * 活跃度 Bitmap TTL（90 天）
     * <p>
     * 用于离线分析和统计，建议保留较长时间
     * </p>
     */
    public static final long ACTIVE_BITMAP_TTL_SECONDS = 90 * 24 * 60 * 60;

    /**
     * BITOP 临时键 TTL（60 秒）
     * <p>
     * BITOP 操作产生的临时键，设置短 TTL 避免内存泄漏
     * </p>
     */
    public static final long BITOP_TEMP_TTL_SECONDS = 60;

    // ========== 活跃度 Bitmap 常量 ==========

    /**
     * 活跃度 Bitmap Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE, date)
     * </p>
     * <p>
     * 示例：fota:active:20260217
     * </p>
     * <p>
     * 说明：设备 ID 作为 bitmap 偏移量，标记设备活跃状态
     * </p>
     */
    public static final String ACTIVE_BITMAP_KEY_TEMPLATE = "fota:active:%s";

    /**
     * BITOP 临时键 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.BITOP_TEMP_KEY_TEMPLATE, operation, identifier)
     * </p>
     * <p>
     * 示例：fota:tmp:bitop:union:20260215-20260217
     * </p>
     * <p>
     * 说明：用于 BITOP 操作的临时键，操作完成后自动过期
     * </p>
     */
    public static final String BITOP_TEMP_KEY_TEMPLATE = "fota:tmp:bitop:%s:%s";

    // ========== 策略快照常量 ==========

    /**
     * 策略快照 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_SNAPSHOT_KEY_TEMPLATE, scope, version)
     * </p>
     * <p>
     * 示例：fota:pol:snap:main:v123
     * </p>
     * <p>
     * 说明：策略快照数据，用于区域配置同步
     * </p>
     */
    public static final String POLICY_SNAPSHOT_KEY_TEMPLATE = "fota:pol:snap:%s:v%s";

    /**
     * 策略快照活跃版本 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_ACTIVE_VER_KEY_TEMPLATE, scope)
     * </p>
     * <p>
     * 示例：fota:pol:active_ver:main
     * </p>
     * <p>
     * 说明：指向当前活跃的策略快照版本，原子切换
     * </p>
     */
    public static final String POLICY_ACTIVE_VER_KEY_TEMPLATE = "fota:pol:active_ver:%s";

    // ========== 限流配额常量 ==========

    /**
     * 策略配额 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_QUOTA_KEY_TEMPLATE, policyId, date)
     * </p>
     * <p>
     * 示例：fota:quota:policy:101:20260217
     * </p>
     * <p>
     * 说明：策略每日配额计数器，用于灰度发布控制
     * </p>
     */
    public static final String POLICY_QUOTA_KEY_TEMPLATE = "fota:quota:policy:%s:%s";

    /**
     * 灰度计数 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.GRAY_COUNT_KEY_TEMPLATE, policyId, date)
     * </p>
     * <p>
     * 示例：fota:gray:count:101:20260217
     * </p>
     * <p>
     * 说明：灰度发布设备计数器，记录命中灰度的设备数量
     * </p>
     */
    public static final String GRAY_COUNT_KEY_TEMPLATE = "fota:gray:count:%s:%s";

    // ========== 服务注册常量 ==========

    /**
     * 服务注册节点 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.REGISTRY_NODE_KEY_TEMPLATE, nodeCode)
     * </p>
     * <p>
     * 示例：fota:registry:node:standalone-1
     * </p>
     */
    public static final String REGISTRY_NODE_KEY_TEMPLATE = "fota:registry:node:%s";

    /**
     * 在线节点集合 Key
     */
    public static final String REGISTRY_NODE_SET_KEY = "fota:registry:nodes";

    /**
     * 分区主节点 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.REGION_LEADER_KEY_TEMPLATE, regionCode)
     * </p>
     * <p>
     * 示例：fota:region:leader:us-east
     * </p>
     */
    public static final String REGION_LEADER_KEY_TEMPLATE = "fota:region:leader:%s";

    /**
     * 分区密钥 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.REGION_SECRET_KEY_TEMPLATE, regionCode)
     * </p>
     * <p>
     * 示例：fota:region:secret:us-east
     * </p>
     */
    public static final String REGION_SECRET_KEY_TEMPLATE = "fota:region:secret:%s";

    /**
     * 分区 nonce Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.REGION_NONCE_KEY_TEMPLATE, regionCode, nonce)
     * </p>
     * <p>
     * 示例：fota:region:nonce:us-east:abc123
     * </p>
     */
    public static final String REGION_NONCE_KEY_TEMPLATE = "fota:region:nonce:%s:%s";

    /**
     * 分区密钥轮换待下发 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.REGION_ROTATE_KEY_TEMPLATE, regionCode)
     * </p>
     * <p>
     * 示例：fota:region:rotate:us-east
     * </p>
     */
    public static final String REGION_ROTATE_KEY_TEMPLATE = "fota:region:rotate:%s";

    // ========== 固件上传会话常量 ==========

    /**
     * 固件上传会话 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, sessionId)
     * </p>
     * <p>
     * 示例：fota:fw:upload:sess:a1b2c3d4e5f6
     * </p>
     * <p>
     * 说明：固件上传会话元数据，包含文件哈希、临时路径等信息
     * </p>
     */
    public static final String FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE = "fota:fw:upload:sess:%s";

    /**
     * 固件上传版本锁 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.FIRMWARE_UPLOAD_LOCK_KEY_TEMPLATE, productId, version)
     * </p>
     * <p>
     * 示例：fota:fw:upload:lock:1001:1.0.0
     * </p>
     * <p>
     * 说明：防止并发上传同一版本号的分布式锁
     * </p>
     */
    public static final String FIRMWARE_UPLOAD_LOCK_KEY_TEMPLATE = "fota:fw:upload:lock:%s:%s";

    /**
     * 固件上传会话 TTL（2 小时）
     * <p>
     * 超时后自动清理临时文件和会话数据
     * </p>
     */
    public static final long FIRMWARE_UPLOAD_SESSION_TTL_SECONDS = 2 * 60 * 60;

    /**
     * 固件上传版本锁 TTL（30 秒）
     * <p>
     * 上传完成后自动释放锁
     * </p>
     */
    public static final long FIRMWARE_UPLOAD_LOCK_TTL_SECONDS = 30;

    // ========== 设备导入会话常量 ==========

    /**
     * 设备导入会话 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.DEVICE_IMPORT_SESSION_KEY_TEMPLATE, sessionId)
     * </p>
     * <p>
     * 示例：fota:device:import:sess:a1b2c3d4e5f6
     * </p>
     * <p>
     * 说明：设备导入会话数据，包含解析后的 IMEI 列表和统计信息
     * </p>
     */
    public static final String DEVICE_IMPORT_SESSION_KEY_TEMPLATE = "fota:device:import:sess:%s";

    /**
     * 设备导入会话 TTL（2 小时）
     * <p>
     * 超时后自动清理会话数据
     * </p>
     */
    public static final long DEVICE_IMPORT_SESSION_TTL_SECONDS = 2 * 60 * 60;
}
