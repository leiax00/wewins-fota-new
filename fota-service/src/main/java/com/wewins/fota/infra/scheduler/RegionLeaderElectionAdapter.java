package com.wewins.fota.infra.scheduler;

import com.wewins.fota.cache.cluster.RegionLeaderService;
import com.wewins.fota.scheduler.leader.LeaderElectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * RegionLeaderService 到 LeaderElectionService 的适配器。
 * <p>
 * 当 {@link RegionLeaderService} 可用时，将其适配为
 * {@link LeaderElectionService} 接口，覆盖框架模块的默认实现。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RegionLeaderService.class)
public class RegionLeaderElectionAdapter implements LeaderElectionService {

    private final RegionLeaderService regionLeaderService;

    @Override
    public boolean isLeader() {
        return regionLeaderService.isLeader();
    }
}
