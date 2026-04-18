package com.wewins.fota.infra.scheduler.job;

import com.wewins.fota.application.statistics.StatisticsSnapshotExecutor;
import com.wewins.fota.scheduler.job.FotaScheduledJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PolicyStatisticsSnapshotJob implements FotaScheduledJob {

    private final StatisticsSnapshotExecutor statisticsSnapshotExecutor;

    @Override
    public String jobName() {
        return "statistics.policy-snapshot";
    }

    @Override
    public void execute() {
        statisticsSnapshotExecutor.refreshPolicySnapshots();
    }
}
