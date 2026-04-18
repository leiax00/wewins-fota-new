package com.wewins.fota.scheduler.job;

/**
 * FOTA 定时任务公共接口。
 * <p>
 * 所有定时任务需实现此接口，并注册为 Spring Bean。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
public interface FotaScheduledJob {

    /**
     * 获取任务名称。
     *
     * @return 任务名称，用于日志和监控
     */
    String jobName();

    /**
     * 执行任务逻辑。
     */
    void execute();
}
