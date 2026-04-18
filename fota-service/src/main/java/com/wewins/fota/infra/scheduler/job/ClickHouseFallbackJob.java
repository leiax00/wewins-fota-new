package com.wewins.fota.infra.scheduler.job;

import com.wewins.fota.infra.persistence.clickhouse.reporting.fallback.FallbackEventReplayService;
import com.wewins.fota.scheduler.job.FotaScheduledJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ClickHouse 降级事件补偿任务。
 * <p>
 * 定期从本地文件读取降级存储的事件，重新写入 ClickHouse。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickHouseFallbackJob implements FotaScheduledJob {

    private final FallbackEventReplayService fallbackEventReplayService;

    @Override
    public String jobName() {
        return "clickhouse.fallback";
    }

    /**
     * 执行 ClickHouse 降级事件重放。
     */
    @Override
    public void execute() {
        fallbackEventReplayService.replayTodayEvents();
    }
}
