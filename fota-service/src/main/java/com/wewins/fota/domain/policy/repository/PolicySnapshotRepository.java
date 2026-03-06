package com.wewins.fota.domain.policy.repository;

import com.wewins.fota.domain.policy.model.entity.PolicySnapshot;

import java.util.Optional;

/**
 * 策略快照仓储接口
 * <p>
 * 定义策略快照的持久化操作，支持：
 * <ul>
 *   <li>写入快照（带版本管理）</li>
 *   <li>读取当前活跃快照</li>
 *   <li>原子切换版本指针</li>
 *   <li>查询同步状态</li>
 * </ul>
 * </p>
 *
 * <h3>Redis 键设计</h3>
 * <pre>
 * fota:pol:active_ver:{productId}  -> 版本号（String）
 * fota:pol:snap:{productId}:v{ver} -> 快照内容（Hash）
 * fota:pol:sync_ts:{productId}     -> 同步时间戳（String）
 * fota:pol:write_lock:{productId}  -> 写入锁（String）
 * </pre>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
public interface PolicySnapshotRepository {

    /**
     * 写入策略快照
     * <p>
     * 将快照内容写入 Redis，但不切换版本指针。
     * 需要配合 {@link #switchVersion} 完成快照发布。
     * </p>
     *
     * @param snapshot 快照数据
     * @return true 表示写入成功
     */
    boolean writeSnapshot(PolicySnapshot snapshot);

    /**
     * 原子切换版本指针
     * <p>
     * 将活跃版本指针指向新版本，保证读取一致性。
     * </p>
     *
     * @param productId 产品 ID
     * @param newVersion 新版本号
     * @return true 表示切换成功
     */
    boolean switchVersion(Long productId, String newVersion);

    /**
     * 写入快照并原子切换版本
     * <p>
     * 使用 Lua 脚本保证原子性，一次性完成：
     * <ol>
     *   <li>写入快照内容</li>
     *   <li>更新同步时间戳</li>
     *   <li>切换版本指针</li>
     *   <li>清理旧版本快照</li>
     * </ol>
     * </p>
     *
     * @param snapshot 快照数据
     * @return true 表示操作成功
     */
    boolean writeAndSwitch(PolicySnapshot snapshot);

    /**
     * 读取当前活跃的快照
     * <p>
     * 根据版本指针读取快照内容。
     * </p>
     *
     * @param productId 产品 ID
     * @return 快照数据，不存在时返回 empty
     */
    Optional<PolicySnapshot> loadSnapshot(Long productId);

    /**
     * 读取指定版本的快照
     *
     * @param productId 产品 ID
     * @param version   版本号
     * @return 快照数据，不存在时返回 empty
     */
    Optional<PolicySnapshot> loadSnapshotByVersion(Long productId, String version);

    /**
     * 获取当前活跃版本号
     *
     * @param productId 产品 ID
     * @return 版本号，不存在时返回 null
     */
    String getActiveVersion(Long productId);

    /**
     * 获取最后同步时间戳
     *
     * @param productId 产品 ID
     * @return Unix 时间戳（秒），未同步时返回 null
     */
    Long getLastSyncTime(Long productId);

    /**
     * 检查同步是否滞后
     *
     * @param productId        产品 ID
     * @param staleAgeSeconds  滞后阈值（秒）
     * @return true 表示同步已滞后
     */
    boolean isSyncStale(Long productId, long staleAgeSeconds);

    /**
     * 清理旧版本快照
     * <p>
     * 保留最近 N 个版本的快照，删除其余版本。
     * </p>
     *
     * @param productId    产品 ID
     * @param keepVersions 保留版本数
     * @return 清理的版本数量
     */
    int cleanupOldVersions(Long productId, int keepVersions);

    /**
     * 删除产品的所有快照
     *
     * @param productId 产品 ID
     * @return true 表示删除成功
     */
    boolean deleteAllSnapshots(Long productId);
}
