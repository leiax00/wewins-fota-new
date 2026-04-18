package com.wewins.fota.scheduler.leader;

/**
 * 默认的 Leader 选举服务实现。
 * <p>
 * 单实例模式下始终返回 true，无需分布式选举。
 * </p>
 * <p>
 * 注意：此类不使用 @Component 注解，而是通过 SchedulerAutoConfiguration
 * 以 @Bean 方式注册，便于应用层通过 @ConditionalOnMissingBean 机制覆盖。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
public class AlwaysLeaderElectionService implements LeaderElectionService {

    @Override
    public boolean isLeader() {
        return true;
    }
}
