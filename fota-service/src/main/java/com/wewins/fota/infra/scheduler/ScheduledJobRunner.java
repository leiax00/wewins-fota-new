package com.wewins.fota.infra.scheduler;

import com.wewins.fota.infra.scheduler.job.ClickHouseFallbackJob;
import com.wewins.fota.infra.scheduler.job.PolicyStatisticsSnapshotJob;
import com.wewins.fota.infra.scheduler.job.ProductVersionStatisticsJob;
import com.wewins.fota.scheduler.job.FotaScheduledJob;
import com.wewins.fota.scheduler.leader.LeaderElectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 业务级定时任务统一运行器。
 * <p>
 * 承接所有业务 Job 的调度入口，在执行前完成 Leader 检查，
 * 确保多实例场景下只有一个节点执行定时任务。
 * </p>
 * <p>
 * 基础设施级任务（如文件清理、Sentinel 规则刷新）留在各自模块，
 * 由各自的 {@code @Scheduled} 注解独立触发。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobRunner {

    private final ClickHouseFallbackJob clickHouseFallbackJob;
    private final PolicyStatisticsSnapshotJob policyStatisticsSnapshotJob;
    private final ProductVersionStatisticsJob productVersionStatisticsJob;
    private final ObjectProvider<LeaderElectionService> leaderElectionServiceProvider;

    /**
     * 执行 ClickHouse 降级事件回放任务。
     * <p>
     * 默认每 5 分钟执行一次。
     * </p>
     */
    @Scheduled(fixedDelayString = "${app.scheduler.clickhouse-fallback.interval-ms:300000}")
    public void runClickHouseFallback() {
        runIfLeader(clickHouseFallbackJob);
    }

    /**
     * 执行统计快照任务。
     * <p>
     * 默认每 5 分钟执行一次。
     * </p>
     */
    @Scheduled(fixedDelayString = "${app.scheduler.statistics-policy-snapshot.interval-ms:300000}")
    public void runPolicyStatisticsSnapshot() {
        runIfLeader(policyStatisticsSnapshotJob);
    }

    /**
     * 执行产品版本设备数快照任务。
     * <p>
     * 默认每天凌晨 1 点执行一次。
     * </p>
     */
    @Scheduled(cron = "${app.scheduler.product-version-snapshot.cron:0 0 1 * * ?}")
    public void runProductVersionStatisticsSnapshot() {
        runIfLeader(productVersionStatisticsJob);
    }

    /**
     * 仅在当前节点为 Leader 时执行任务。
     *
     * @param job 要执行的任务
     */
    private void runIfLeader(FotaScheduledJob job) {
        LeaderElectionService leaderService = leaderElectionServiceProvider.getIfAvailable();
        if (leaderService != null && !leaderService.isLeader()) {
            log.debug("定时任务跳过执行: jobName={}, reason=not_leader", job.jobName());
            return;
        }

        try {
            log.info("定时任务开始执行: jobName={}", job.jobName());
            job.execute();
            log.info("定时任务执行完成: jobName={}", job.jobName());
        } catch (Exception e) {
            log.error("定时任务执行失败: jobName={}", job.jobName(), e);
        }
    }
}
