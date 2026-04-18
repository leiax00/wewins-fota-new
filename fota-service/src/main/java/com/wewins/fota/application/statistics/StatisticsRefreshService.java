package com.wewins.fota.application.statistics;

import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 统计刷新服务。
 */
@Slf4j
@Service
public class StatisticsRefreshService {

    private static final long DEBOUNCE_LOCK_TTL_SECONDS = 30L;
    private static final long RUNNING_LOCK_TTL_SECONDS = 120L;

    private static final String DEBOUNCE_LOCK_PREFIX = "fota:stats:debounce:";
    private static final String RUNNING_LOCK_PREFIX = "fota:stats:running:";

    private static final Set<String> SUPPORTED_SCOPES = Set.of("product", "policy", "firmware", "all");

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> releaseLockScript;
    private final Executor taskExecutor;
    private final StatisticsSnapshotExecutor statisticsSnapshotExecutor;

    public StatisticsRefreshService(
            StringRedisTemplate stringRedisTemplate,
            DefaultRedisScript<Long> releaseLockScript,
            @Qualifier("fotaTaskExecutor") Executor taskExecutor,
            StatisticsSnapshotExecutor statisticsSnapshotExecutor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.releaseLockScript = releaseLockScript;
        this.taskExecutor = taskExecutor;
        this.statisticsSnapshotExecutor = statisticsSnapshotExecutor;
    }

    /**
     * 手动触发统计刷新。
     *
     * @param scope   刷新范围：product/policy/firmware/all
     * @param scopeId 范围 ID，scope=all 时允许为空
     * @return 触发结果
     */
    public ApiResponse<Void> refresh(String scope, Long scopeId) {
        String normalizedScope = normalizeScope(scope);
        if (!isValidScope(normalizedScope)) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(),
                    "scope 仅支持 product、policy、firmware、all");
        }

        if ("all".equals(normalizedScope)) {
            if (scopeId != null) {
                return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "scope=all 时 scopeId 必须为空");
            }
        } else if (scopeId == null || scopeId <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "scopeId 必须为正整数");
        }

        String debounceLockName = buildLockName(DEBOUNCE_LOCK_PREFIX, normalizedScope, scopeId);
        String debounceToken = tryAcquireLock(debounceLockName, DEBOUNCE_LOCK_TTL_SECONDS);
        if (debounceToken == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "操作太频繁，请稍后再试");
        }

        String runningLockName = buildLockName(RUNNING_LOCK_PREFIX, normalizedScope, scopeId);
        String runningToken = tryAcquireLock(runningLockName, RUNNING_LOCK_TTL_SECONDS);
        if (runningToken == null) {
            releaseLock(debounceLockName, debounceToken);
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "统计计算中，请稍后再试");
        }

        CompletableFuture.runAsync(
                () -> executeRefresh(normalizedScope, scopeId, runningLockName, runningToken),
                taskExecutor
        );

        log.info("手动触发统计刷新: scope={}, scopeId={}", normalizedScope, scopeId);
        return ApiResponse.success("统计刷新任务已提交", null);
    }

    private void executeRefresh(String scope, Long scopeId, String runningLockName, String runningLockToken) {
        try {
            doRefresh(scope, scopeId);
            log.info("统计刷新执行完成: scope={}, scopeId={}", scope, scopeId);
        } catch (Exception e) {
            log.error("统计刷新执行失败: scope={}, scopeId={}", scope, scopeId, e);
        } finally {
            boolean released = releaseLock(runningLockName, runningLockToken);
            if (!released) {
                log.warn("统计刷新执行锁释放失败: lockName={}, scope={}, scopeId={}",
                        runningLockName, scope, scopeId);
            }
        }
    }

    /**
     * 统计计算编排入口。
     * <p>
     * 当前仅保留触发入口与锁控制，具体重算逻辑后续在此扩展。
     * </p>
     */
    private void doRefresh(String scope, Long scopeId) {
        log.info("开始执行统计刷新: scope={}, scopeId={}", scope, scopeId);
        switch (scope) {
            case "policy" -> log.info("策略统计刷新跳过持久化: policyId={}", scopeId);
            case "product", "firmware" -> statisticsSnapshotExecutor.refreshProductVersionCounts();
            case "all" -> statisticsSnapshotExecutor.refreshProductVersionCounts();
            default -> throw new IllegalArgumentException("Unsupported scope: " + scope);
        }
    }

    private String normalizeScope(String scope) {
        return scope == null ? null : scope.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidScope(String scope) {
        return scope != null && SUPPORTED_SCOPES.contains(scope);
    }

    private String buildLockName(String prefix, String scope, Long scopeId) {
        if (scopeId == null) {
            return prefix + scope;
        }
        return prefix + scope + ":" + scopeId;
    }

    private String tryAcquireLock(String key, long expireSeconds) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, token, expireSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(locked) ? token : null;
        } catch (Exception e) {
            log.error("获取统计刷新锁异常: key={}", key, e);
            return null;
        }
    }

    private boolean releaseLock(String key, String token) {
        try {
            Long result = stringRedisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(key),
                    token
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("释放统计刷新锁异常: key={}", key, e);
            return false;
        }
    }
}
