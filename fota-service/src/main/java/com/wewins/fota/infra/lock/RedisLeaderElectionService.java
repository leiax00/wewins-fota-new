package com.wewins.fota.infra.lock;

import com.wewins.fota.infra.config.ClusterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 的 Leader 选举服务实现
 * <p>
 * 使用 Redis 存储和续约机制实现简单的 Leader 选举
 * </p>
 * <p>
 * 实现细节：
 * <ul>
 *   <li>每个任务有独立的 Redis 键存储 Leader 实例 ID</li>
 *   <li>Leader 定期续约（renewInterval 秒）以维持身份</li>
 *   <li>其他实例通过检查键是否存在判断 Leader 状态</li>
 *   <li>使用 Redis TTL 自动过期，防止 Leader 崩溃后身份永久持有</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLeaderElectionService implements LeaderElectionService {

    private final StringRedisTemplate<String, String> redisTemplate;
    private final ClusterProperties clusterProperties;

    private static final String LEADER_KEY_PREFIX = "fota:leader:";

    @Override
    public boolean isLeader(String taskId) {
        if (!clusterProperties.isEnabled()) {
            log.debug("集群模式未启用，跳过 Leader 选举检查: taskId={}", taskId);
            return false;
        }

        if (!clusterProperties.getLeader().isEnabled()) {
            log.debug("Leader 选举功能未启用，默认当前实例是 Leader: taskId={}", taskId);
            return true;
        }

        String leaderKey = buildLeaderKey(taskId);

        try {
            // 检查当前实例是否持有 Leader 身份
            String currentLeaderId = redisTemplate.opsForValue().get(leaderKey);
            boolean isLeader = getCurrentInstanceId().equals(currentLeaderId);

            log.debug("Leader 选举检查: taskId={}, leaderId={}, isLeader={}",
                    taskId, currentLeaderId, isLeader);

            return isLeader;

        } catch (Exception e) {
            log.error("检查 Leader 身份异常: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public boolean renewLeadership(String taskId) {
        if (!clusterProperties.isEnabled() || !clusterProperties.getLeader().isEnabled()) {
            log.debug("Leader 选举功能未启用，跳过续约: taskId={}", taskId);
            return true;
        }

        String leaderKey = buildLeaderKey(taskId);
        String instanceId = getCurrentInstanceId();
        long renewInterval = Duration.ofSeconds(clusterProperties.getLeader().getRenewInterval()).toMillis();

        try {
            // 设置 Leader 身份并更新 TTL
            redisTemplate.opsForValue().set(leaderKey, instanceId, renewInterval);

            log.debug("成功续约 Leader 身份: taskId={}, instanceId={}, renewInterval={}ms",
                    taskId, instanceId, renewInterval);

            return true;

        } catch (Exception e) {
            log.error("续约 Leader 身份异常: taskId={}, instanceId={}", taskId, instanceId, e);
            return false;
        }
    }

    @Override
    public void relinquishLeadership(String taskId) {
        if (!clusterProperties.isEnabled() || !clusterProperties.getLeader().isEnabled()) {
            log.debug("Leader 选举功能未启用，跳过放弃: taskId={}", taskId);
            return;
        }

        String leaderKey = buildLeaderKey(taskId);

        try {
            // 删除 Leader 键，放弃身份
            redisTemplate.delete(leaderKey);

            log.info("已放弃 Leader 身份: taskId={}, instanceId={}", taskId, getCurrentInstanceId());

        } catch (Exception e) {
            log.error("放弃 Leader 身份异常: taskId={}", taskId, e);
        }
    }

    /**
     * 构建完整的 Leader 键名
     *
     * @param taskId 任务标识符
     * @return Leader 键名
     */
    private String buildLeaderKey(String taskId) {
        return LEADER_KEY_PREFIX + taskId;
    }

    /**
     * 获取当前实例的唯一标识符
     * <p>
     * 使用实例标识符来区分不同的实例
     * 可以使用 hostname + PID 或 UUID
     * </p>
     *
     * @return 实例标识符
     */
    private String getCurrentInstanceId() {
        // 简单实现：使用 hostname + PID
        // 生产环境建议使用 UUID 或配置的实例 ID
        String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");
        String pid = Long.toString(ProcessHandle.current().pid());
        return hostname + "-" + pid;
    }
}
