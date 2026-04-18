package com.wewins.fota.cache.cluster;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.common.region.RegionCodeResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Region Leader 选举服务。
 * <p>
 * 基于 Redis 的分布式 Leader 选举，在所有部署模式下可用。
 * 由配置 {@code app.leader.enabled} 决定是否启用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.leader", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RegionLeaderService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ClusterProperties clusterProperties;

    private String leaderKey;
    private String token;

    @PostConstruct
    public void init() {
        String nodeCode = clusterProperties.getNode().getCode();
        String regionCode = RegionCodeResolver.resolveRegionCode(nodeCode);
        this.leaderKey = String.format(RedisKeyConstants.REGION_LEADER_KEY_TEMPLATE, regionCode);
        this.token = regionCode + ":" + nodeCode + ":" + UUID.randomUUID();
        tryAcquire();
    }

    @Scheduled(fixedDelayString = "${app.leader.renew-interval-ms:10000}")
    public void heartbeat() {
        if (isLeader()) {
            renew();
            return;
        }
        tryAcquire();
    }

    @PreDestroy
    public void onShutdown() {
        release();
    }

    public boolean isLeader() {
        Object value = redisTemplate.opsForValue().get(leaderKey);
        return token.equals(String.valueOf(value));
    }

    private void tryAcquire() {
        Duration ttl = Duration.ofSeconds(clusterProperties.getLeader().getTtlSeconds());
        Boolean success = redisTemplate.opsForValue().setIfAbsent(leaderKey, token, ttl);
        if (Boolean.TRUE.equals(success)) {
            log.info("Acquired region leader lock: key={}", leaderKey);
        }
    }

    private void renew() {
        Object value = redisTemplate.opsForValue().get(leaderKey);
        if (!token.equals(String.valueOf(value))) {
            return;
        }
        Duration ttl = Duration.ofSeconds(clusterProperties.getLeader().getTtlSeconds());
        redisTemplate.expire(leaderKey, ttl);
    }

    private void release() {
        Object value = redisTemplate.opsForValue().get(leaderKey);
        if (!token.equals(String.valueOf(value))) {
            return;
        }
        redisTemplate.delete(leaderKey);
    }
}
