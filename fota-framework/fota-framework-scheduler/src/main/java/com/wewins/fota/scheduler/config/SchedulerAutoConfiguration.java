package com.wewins.fota.scheduler.config;

import com.wewins.fota.scheduler.leader.AlwaysLeaderElectionService;
import com.wewins.fota.scheduler.leader.LeaderElectionService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度器模块自动配置。
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(SchedulerProperties.class)
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerAutoConfiguration {

    /**
     * 默认的 Leader 选举服务。
     * <p>
     * 单实例模式下始终返回 true，多实例模式下由 fota-service 提供 RegionLeaderService 适配器覆盖。
     * </p>
     *
     * @return 默认 Leader 选举服务
     */
    @Bean
    @ConditionalOnMissingBean(LeaderElectionService.class)
    public LeaderElectionService leaderElectionService() {
        return new AlwaysLeaderElectionService();
    }
}
