package com.wewins.fota.infra.leader;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.infra.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import com.wewins.fota.infra.region.RegionCodeResolver;
import java.util.UUID;

/**
 * 分区主节点选举
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.leader", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "app.mode", havingValue = "region")
public class RegionLeaderService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    private String leaderKey;
    private String token;

    @PostConstruct
    public void init() {
        String regionCode = RegionCodeResolver.resolveRegionCode(appProperties.getNode().getCode());
        this.leaderKey = String.format(RedisKeyConstants.REGION_LEADER_KEY_TEMPLATE, regionCode);
        String instanceId = appProperties.getRegistry().getInstanceId();
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
        Duration ttl = Duration.ofSeconds(appProperties.getLeader().getTtlSeconds());
        Boolean success = redisTemplate.opsForValue().setIfAbsent(leaderKey, token, ttl);
        if (Boolean.TRUE.equals(success)) {
            log.info("获得分区主节点锁: key={}, token={}", leaderKey, token);
        }
    }

    private void renew() {
        Object value = redisTemplate.opsForValue().get(leaderKey);
        if (!token.equals(String.valueOf(value))) {
            return;
        }
        Duration ttl = Duration.ofSeconds(appProperties.getLeader().getTtlSeconds());
        Boolean success = redisTemplate.expire(leaderKey, ttl);
        if (Boolean.TRUE.equals(success)) {
            log.debug("续约分区主节点锁: key={}", leaderKey);
        }
    }

    private void release() {
        Object value = redisTemplate.opsForValue().get(leaderKey);
        if (!token.equals(String.valueOf(value))) {
            return;
        }
        redisTemplate.delete(leaderKey);
        log.info("释放分区主节点锁: key={}", leaderKey);
    }

}
