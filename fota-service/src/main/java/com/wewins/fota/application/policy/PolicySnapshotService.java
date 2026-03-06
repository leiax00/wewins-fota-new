package com.wewins.fota.application.policy;

import com.wewins.fota.domain.policy.model.entity.PolicySnapshot;

import java.util.Optional;

/**
 * 策略快照应用服务接口
 * <p>
 * 提供策略快照的读写和版本管理功能，支持：
 * <ul>
 *   <li>写入快照并原子切换版本</li>
 *   <li>读取当前活跃快照</li>
 *   <li>降级策略（Redis 不可用时）</li>
 *   <li>同步状态监控</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
public interface PolicySnapshotService {

    /**
     * 发布策略快照
     * <p>
     * 将快照写入 Redis 并原子切换版本指针。
     * </p>
     *
     * @param snapshot 快照数据
     * @return true 表示发布成功
     */
    boolean publishSnapshot(PolicySnapshot snapshot);

    /**
     * 加载产品的策略快照
     * <p>
     * 优先从 Redis 读取，Redis 不可用时降级到数据库。
     * </p>
     *
     * @param productId 产品 ID
     * @return 快照数据
     */
    Optional<PolicySnapshot> loadSnapshot(Long productId);

    /**
     * 获取当前活跃版本号
     *
     * @param productId 产品 ID
     * @return 版本号，不存在时返回 null
     */
    String getActiveVersion(Long productId);

    /**
     * 检查同步状态
     *
     * @param productId 产品 ID
     * @return 同步状态信息
     */
    SyncStatus getSyncStatus(Long productId);

    /**
     * 清理旧版本快照
     *
     * @param productId 产品 ID
     * @return 清理的版本数量
     */
    int cleanupOldSnapshots(Long productId);

    /**
     * 删除产品的所有快照
     *
     * @param productId 产品 ID
     * @return true 表示删除成功
     */
    boolean deleteAllSnapshots(Long productId);

    /**
     * 同步状态
     */
    record SyncStatus(
            boolean isActive,
            String currentVersion,
            Long lastSyncTime,
            boolean isStale,
            long staleSeconds
    ) {
        /**
         * 创建活跃状态
         */
        public static SyncStatus active(String version, Long lastSyncTime) {
            return new SyncStatus(true, version, lastSyncTime, false, 0);
        }

        /**
         * 创建滞后状态
         */
        public static SyncStatus stale(String version, Long lastSyncTime, long staleSeconds) {
            return new SyncStatus(true, version, lastSyncTime, true, staleSeconds);
        }

        /**
         * 创建非活跃状态
         */
        public static SyncStatus inactive() {
            return new SyncStatus(false, null, null, false, 0);
        }
    }
}
