package com.wewins.fota.infra.lock;

/**
 * Leader 选举服务接口
 * <p>
 * 提供分布式环境下的主节点选举能力
 * 用于协调集群中的定时任务执行
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 *   <li>配置同步任务 - 只有 leader 执行定时拉取</li>
 *   <li>数据聚合任务 - 只有 leader 执行数据汇总</li>
 *   <li>配额清理任务 - 只有 leader 执行清理</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public interface LeaderElectionService {

    /**
     * 检查当前实例是否是 Leader
     * <p>
     * 通过检查 Redis 中的续约键判断是否仍有效
     * </p>
     *
     * @param taskId 任务标识符（用于区分不同的任务）
     * @return true 如果当前实例是该任务的 Leader
     */
    boolean isLeader(String taskId);

    /**
     * 续约 Leader 身份
     * <p>
     * 需要定期调用以维持 Leader 身份
     * </p>
     *
     * @param taskId 任务标识符
     * @return true 如果续约成功
     */
    boolean renewLeadership(String taskId);

    /**
     * 放弃 Leader 身份
     * <p>
     * 当实例停止或不再执行任务时调用
     * </p>
     *
     * @param taskId 任务标识符
     */
    void relinquishLeadership(String taskId);
}
