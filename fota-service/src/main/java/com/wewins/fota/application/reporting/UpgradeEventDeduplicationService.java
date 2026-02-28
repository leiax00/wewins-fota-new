package com.wewins.fota.application.reporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 升级事件去重服务
 * <p>
 * 使用 Redis SET 存储已处理的 event_id，防止重复处理
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>使用 Redis SET 存储 eventId，键模式：fota:event:dedup:{date}</li>
 *   <li>按日期分桶，每天一个 SET，方便过期清理</li>
 *   <li>TTL 设置为 48 小时，确保跨天重试也能去重</li>
 *   <li>批量检查和批量添加，提高性能</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeEventDeduplicationService {

    private static final String DEDUP_KEY_PREFIX = "fota:event:dedup:";
    private static final Duration TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;

    /**
     * 过滤重复的事件 ID
     * <p>
     * 返回未处理过的事件 ID（不在 Redis SET 中的）
     * </p>
     *
     * @param eventIds 事件 ID 列表
     * @return 未处理过的事件 ID 列表
     */
    public List<String> filterNewEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        String key = getTodayDedupKey();

        // 批量检查存在的 ID
        Set<String> existingIds = redisTemplate.opsForSet().members(key);
        if (existingIds == null || existingIds.isEmpty()) {
            // 如果 SET 为空或不存在，全部都是新事件
            return new ArrayList<>(eventIds);
        }

        // 过滤已存在的 ID
        return eventIds.stream()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * 标记事件为已处理
     * <p>
     * 将事件 ID 添加到 Redis SET 中
     * </p>
     *
     * @param eventIds 事件 ID 列表
     */
    public void markAsProcessed(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }

        String key = getTodayDedupKey();

        // 批量添加到 SET
        redisTemplate.opsForSet().add(key, eventIds.toArray(new String[0]));

        // 设置过期时间（幂等操作，多次设置不影响）
        redisTemplate.expire(key, TTL);

        log.debug("标记事件为已处理: count={}, key={}", eventIds.size(), key);
    }

    /**
     * 检查单个事件是否已处理
     *
     * @param eventId 事件 ID
     * @return true=已处理，false=未处理
     */
    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return false;
        }

        String key = getTodayDedupKey();
        Boolean isMember = redisTemplate.opsForSet().isMember(key, eventId);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 获取今天的去重键
     * <p>
     * 格式：fota:event:dedup:yyyy-MM-dd
     * </p>
     *
     * @return Redis SET 键
     */
    private String getTodayDedupKey() {
        String today = java.time.LocalDate.now().toString();
        return DEDUP_KEY_PREFIX + today;
    }

    /**
     * 清理指定日期的去重记录
     * <p>
     * 一般不需要手动调用，依赖 TTL 自动过期
     * </p>
     *
     * @param date 日期
     */
    public void cleanupByDate(java.time.LocalDate date) {
        String key = DEDUP_KEY_PREFIX + date.toString();
        redisTemplate.delete(key);
        log.info("清理去重记录: date={}, key={}", date, key);
    }
}
