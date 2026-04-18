package com.wewins.fota.scheduler.leader;

/**
 * Leader 选举服务接口。
 * <p>
 * 用于在多实例部署场景下确保定时任务只在 Leader 节点执行。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
public interface LeaderElectionService {

    /**
     * 判断当前节点是否为 Leader。
     *
     * @return true 如果当前节点是 Leader
     */
    boolean isLeader();
}
