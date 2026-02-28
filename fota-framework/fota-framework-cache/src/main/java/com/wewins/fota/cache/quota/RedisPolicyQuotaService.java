package com.wewins.fota.cache.quota;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 策略配额服务 Redis 实现
 * <p>
 * 基于 Redis + Lua 脚本实现原子性配额检查和递增：
 * <ul>
 *   <li>使用 Lua 脚本保证 GET + INCR + EXPIRE 的原子性</li>
 *   <li>支持按日期隔离的配额计数（每日自动重置）</li>
 *   <li>异常降级：Redis 不可用时允许通过（记录日志）</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPolicyQuotaService implements PolicyQuotaService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> quotaCheckAndIncrementScript;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public boolean checkAndIncrementQuota(Long policyId, int maxQuota) {
        if (policyId == null) {
            log.warn("策略 ID 为空，配额检查失败");
            return false;
        }

        if (maxQuota <= 0) {
            log.warn("最大配额值无效: policyId={}, maxQuota={}", policyId, maxQuota);
            return false;
        }

        try {
            String key = buildQuotaKey(policyId);

            // 执行 Lua 脚本：检查配额并原子递增
            Long result = redisTemplate.execute(
                    quotaCheckAndIncrementScript,
                    Collections.singletonList(key),
                    String.valueOf(maxQuota),
                    String.valueOf(getDailyTtlSeconds())
            );

            if (result == null) {
                log.warn("配额检查返回值为空: policyId={}", policyId);
                return false;
            }

            // result == 1 表示配额可用且已递增
            // result == 0 表示配额已用尽
            boolean allowed = result == 1L;

            if (!allowed) {
                log.debug("配额已用尽: policyId={}, maxQuota={}", policyId, maxQuota);
            }

            return allowed;

        } catch (Exception e) {
            log.error("配额检查异常: policyId={}", policyId, e);
            // 异常降级：允许请求通过（避免因配额服务异常阻断升级流程）
            return true;
        }
    }

    @Override
    public long getCurrentUsage(Long policyId) {
        if (policyId == null) {
            return 0;
        }

        try {
            String key = buildQuotaKey(policyId);
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return 0;
            }

            return Long.parseLong(value);

        } catch (NumberFormatException e) {
            log.warn("配额计数值格式错误: policyId={}", policyId, e);
            return 0;
        } catch (Exception e) {
            log.error("获取配额使用量异常: policyId={}", policyId, e);
            return 0;
        }
    }

    @Override
    public void resetQuota(Long policyId) {
        if (policyId == null) {
            return;
        }

        try {
            String key = buildQuotaKey(policyId);
            redisTemplate.delete(key);
            log.info("配额已重置: policyId={}", policyId);

        } catch (Exception e) {
            log.error("重置配额异常: policyId={}", policyId, e);
        }
    }

    /**
     * 构建配额 Redis Key
     * <p>
     * 格式：fota:quota:policy:{policyId}:{date}
     * </p>
     * <p>
     * 包含日期后缀，实现每日自动重置配额
     * </p>
     *
     * @param policyId 策略 ID
     * @return Redis Key
     */
    private String buildQuotaKey(Long policyId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return String.format(RedisKeyConstants.POLICY_QUOTA_KEY_TEMPLATE, policyId, date);
    }

    /**
     * 计算配额 Key 的 TTL（到当天结束的秒数）
     * <p>
     * 确保 Key 在当天 23:59:59 后自动过期，实现每日配额重置
     * </p>
     *
     * @return TTL 秒数
     */
    private int getDailyTtlSeconds() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        int secondsUntilTomorrow = (int) ((tomorrow.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC)
                - System.currentTimeMillis() / 1000));
        return Math.max(secondsUntilTomorrow, 60); // 最小 60 秒
    }
}
