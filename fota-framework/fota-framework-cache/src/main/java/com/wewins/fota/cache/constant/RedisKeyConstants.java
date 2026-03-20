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
 * 缓存模式规范：
 * <ul>
 *   <li>单个对象缓存：fota:{module}:{id} - Type: String (JSON)</li>
 *   <li>列表缓存：fota:cache:list:{module}:{parentId} - Type: Set (存 ID 列表)</li>
 *   <li>缓存索引：fota:cache:index:{module}:{id} - Type: Set (存相关缓存 Key)</li>
 * </ul>
 * </p>
 * <p>
 * 示例：
 * <ul>
 *   <li>fota:device:{imei} - 设备信息缓存</li>
 *   <li>fota:policy:{policyId} - 策略缓存</li>
 *   <li>fota:product:{productId} - 产品信息缓存</li>
 *   <li>fota:firmware:{versionId} - 固件信息缓存</li>
 *   <li>fota:cache:list:product:policy:{productId}:{type} - 产品策略列表（ID集合）</li>
 *   <li>fota:cache:index:product:{productId} - 产品缓存索引</li>
 *   <li>fota:config:{region} - 配置快照</li>
 * </ul>
 * </p>
 * <p>
 * 详细规范参考：docs/03-standards/redis-cache-standards.md
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
     * 使用方式：String.format(RedisKeyConstants.LOCK_KEY_TEMPLATE, lockName)
     * </p>
     * <p>
     * 示例：fota:lock:device_import
     * </p>
     */
    public static final String LOCK_KEY_TEMPLATE = "fota:lock:%s";

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

    public static final long DEVICE_NOT_FOUND_TTL_SECONDS = 60;

    public static final long DEFAULT_NOT_FOUND_TTL_SECONDS = 60;

    /**
     * 策略信息缓存 TTL（24 小时）
     * <p>
     * 滑动 TTL（续期），热点数据持续缓存
     * </p>
     */
    public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;


    /*
    * 策略负缓存ttl
    */
    public static final long POLICY_EMPTY_CACHE_TTL_SECONDS = DEFAULT_NOT_FOUND_TTL_SECONDS;
    /**
     * 产品信息缓存 TTL（7 天）
     * <p>
     * 滑动 TTL（续期），产品信息极少变更
     * </p>
     */
    public static final long PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60;

    /*
    * 产品负缓存TTL
    */
    public static final long PRODUCT_MODEL_NOT_FOUND_TTL_SECONDS = DEFAULT_NOT_FOUND_TTL_SECONDS;

    /**
     * 固件信息缓存 TTL（30 天）
     * <p>
     * 滑动 TTL（续期），固件发布后基本不变
     * </p>
     */
    public static final long FIRMWARE_CACHE_TTL_SECONDS = 30L * 24 * 60 * 60;

    /**
     * 固件版本映射缓存 TTL（24 小时）
     * <p>
     * 映射关系：productId + version + internalVersion -> versionId
     * </p>
     */
    public static final long FIRMWARE_LOOKUP_CACHE_TTL_SECONDS = 24 * 60 * 60;

    /**
     * 固件版本映射负缓存 TTL（60 秒）
     * <p>
     * 用于防止不存在版本反复穿透数据库
     * </p>
     */
    public static final long FIRMWARE_LOOKUP_NOT_FOUND_TTL_SECONDS = DEFAULT_NOT_FOUND_TTL_SECONDS;

    /**
     * 配置信息缓存 TTL（6 小时）
     */
    public static final long CONFIG_CACHE_TTL_SECONDS = 6 * 60 * 60;

    /**
     * CDN Worker 配置缓存 Key
     */
    public static final String CDN_WARM_WORKER_CONFIG_CACHE_KEY = "fota:config:cdn:warm:worker";

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

    /**
     * 策略快照同步时间戳 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_SYNC_TS_KEY_TEMPLATE, productId)
     * </p>
     * <p>
     * 示例：fota:pol:sync_ts:1001
     * </p>
     * <p>
     * 说明：记录最后一次成功同步的时间戳，用于判断是否需要降级
     * </p>
     */
    public static final String POLICY_SYNC_TS_KEY_TEMPLATE = "fota:pol:sync_ts:%s";

    /**
     * 策略快照写入锁 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_WRITE_LOCK_KEY_TEMPLATE, productId)
     * </p>
     * <p>
     * 示例：fota:pol:write_lock:1001
     * </p>
     * <p>
     * 说明：防止多个版本同时写入快照的分布式锁
     * </p>
     */
    public static final String POLICY_WRITE_LOCK_KEY_TEMPLATE = "fota:pol:write_lock:%s";

    /**
     * 策略快照 TTL（7 天）
     * <p>
     * 快照保留时间，超过此时间自动过期
     * </p>
     */
    public static final long POLICY_SNAPSHOT_TTL_SECONDS = 7 * 24 * 60 * 60;

    /**
     * 策略快照同步时间戳 TTL（30 天）
     */
    public static final long POLICY_SYNC_TS_TTL_SECONDS = 30L * 24 * 60 * 60;

    /**
     * 策略快照写入锁 TTL（30 秒）
     */
    public static final long POLICY_WRITE_LOCK_TTL_SECONDS = 30;

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

    // ========== 缓存索引常量 ==========

    /**
     * 产品策略列表缓存 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, type)
     * </p>
     * <p>
     * 示例：fota:cache:list:product:policy:1001:all
     * </p>
     * <p>
     * 说明：缓存产品的策略列表（ID集合），type 为 "all" 或 "prod" 区分是否包含测试策略
     * </p>
     */
    public static final String PRODUCT_POLICY_LIST_KEY_TEMPLATE = "fota:cache:list:product:policy:%s:%s";

    /**
     * 产品型号索引 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.PRODUCT_MODEL_INDEX_KEY_TEMPLATE, model)
     * </p>
     * <p>
     * 示例：fota:cache:product:model:M476
     * </p>
     * <p>
     * 说明：通过产品型号反向查找产品 ID
     * </p>
     */
    public static final String PRODUCT_MODEL_INDEX_KEY_TEMPLATE = "fota:cache:product:model:%s";

    /**
     * 产品缓存索引 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId)
     * </p>
     * <p>
     * 示例：fota:cache:index:product:1001
     * </p>
     * <p>
     * 说明：记录产品相关的所有缓存键，支持批量失效
     * </p>
     */
    public static final String PRODUCT_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:product:%s";

    /**
     * 策略缓存索引 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.POLICY_CACHE_INDEX_KEY_TEMPLATE, policyId)
     * </p>
     * <p>
     * 示例：fota:cache:index:policy:101
     * </p>
     * <p>
     * 说明：记录策略相关的所有缓存键，支持批量失效
     * </p>
     */
    public static final String POLICY_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:policy:%s";

    /**
     * 缓存索引通用 TTL（24 小时）
     * <p>
     * 与策略缓存 TTL 保持一致，确保索引不会比缓存更早过期
     * </p>
     */
    public static final long CACHE_INDEX_TTL_SECONDS = 24 * 60 * 60;

    // ========== 固件缓存常量 ==========

    /**
     * 固件信息 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.FIRMWARE_KEY_TEMPLATE, versionId)
     * </p>
     * <p>
     * 示例：fota:firmware:201
     * </p>
     * <p>
     * 说明：单个固件版本的完整信息缓存
     * </p>
     */
    public static final String FIRMWARE_KEY_TEMPLATE = "fota:firmware:%s";

    /**
     * 固件版本映射 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.FIRMWARE_LOOKUP_KEY_TEMPLATE, productId, version, internalVersion)
     * </p>
     * <p>
     * 示例：fota:cache:firmware:lookup:1001:Mobile.Router.B03:ASR_YEMEN_M476_V11_B03_Build02
     * </p>
     */
    public static final String FIRMWARE_LOOKUP_KEY_TEMPLATE = "fota:cache:firmware:lookup:%s:%s:%s";

    // ========== Sprint 4: 控制参数常量 ==========

    /**
     * 控制参数 TTL（7 天）
     * <p>
     * 控制参数变更频率较低，可长期缓存
     * </p>
     */
    public static final long CTRL_TTL_SECONDS = 7 * 24 * 60 * 60;

    /**
     * 当前生效的负载控制快照 Key。
     */
    public static final String LOAD_CONTROL_ACTIVE_CONFIG_KEY = "fota:load-control:config:active";

    /**
     * 负载控制快照版本号 Key。
     */
    public static final String LOAD_CONTROL_CONFIG_VERSION_KEY = "fota:load-control:config:version";

    /**
     * 最近一次发布时间 Key。
     */
    public static final String LOAD_CONTROL_LAST_PUBLISH_AT_KEY = "fota:load-control:config:last-publish-at";

    // ========== Sprint 4: 负载历史常量 ==========

    /**
     * 负载历史 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.LOAD_HISTORY_KEY_TEMPLATE, date)
     * </p>
     * <p>
     * 示例：fota:load:history:20260309
     * </p>
     * <p>
     * 说明：存储每日负载历史数据（JSON 数组），用于趋势分析
     * </p>
     */
    public static final String LOAD_HISTORY_KEY_TEMPLATE = "fota:load:history:%s";

    /**
     * 负载历史 TTL（7 天）
     * <p>
     * 保留最近 7 天的负载数据用于趋势分析
     * </p>
     */
    public static final long LOAD_HISTORY_TTL_SECONDS = 7 * 24 * 60 * 60;

    // ========== Sprint 4: Sentinel 规则常量 ==========

    /**
     * Sentinel 规则 Key 模板
     * <p>
     * 使用方式：String.format(RedisKeyConstants.SENTINEL_RULE_KEY_TEMPLATE, ruleName)
     * </p>
     * <p>
     * 示例：fota:sentinel:rule:upgrade_check
     * </p>
     * <p>
     * 说明：存储 Sentinel 限流/熔断规则（JSON）
     * </p>
     */
    public static final String SENTINEL_RULE_KEY_TEMPLATE = "fota:sentinel:rule:%s";

    /**
     * Sentinel 规则列表 Key
     * <p>
     * 示例：fota:sentinel:rules
     * </p>
     * <p>
     * 说明：存储所有规则名称的列表，用于批量加载
     * </p>
     */
    public static final String SENTINEL_RULES_LIST_KEY = "fota:sentinel:rules";

    /**
     * Sentinel 规则 TTL（7 天）
     * <p>
     * 规则变更后需更新，保持较长时间避免频繁加载
     * </p>
     */
    public static final long SENTINEL_RULE_TTL_SECONDS = 7 * 24 * 60 * 60;

}
