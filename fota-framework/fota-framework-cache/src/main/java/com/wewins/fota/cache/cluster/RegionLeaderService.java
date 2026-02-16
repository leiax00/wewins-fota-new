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
 * Region leader election service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.leader", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "app.mode", havingValue = "region")
public class RegionLeaderService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ClusterProperties clusterProperties;

    private String leaderKey;
    private String token;

    @PostConstruct
    public void init() {
        String regionCode = RegionCodeResolver.resolveRegionCode(clusterProperties.getNode().getCode());
        this.leaderKey = String.format(RedisKeyConstants.REGION_LEADER_KEY_TEMPLATE, regionCode);
        String instanceId = clusterProperties.getRegistry().getInstanceId();
        this.token = regionCode + ":" + instanceId + ":" + UUID.randomUUID();
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
