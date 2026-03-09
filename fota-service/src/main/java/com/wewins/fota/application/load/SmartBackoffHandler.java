package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.BackoffResult;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 智能退避处理器
 * <p>
 * 当请求被限流或熔断时，计算动态退避时间
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartBackoffHandler {

    private final SystemLoadIndicator loadIndicator;

    private static final int MIN_BACKOFF = 60;
    private static final int MAX_BACKOFF = 7200;

    private static final int BASE_BACKOFF_QPS = 60;
    private static final int BASE_BACKOFF_CONCURRENCY = 120;
    private static final int BASE_BACKOFF_DEGRADE = 300;
    private static final int BASE_BACKOFF_SYSTEM = 600;

    public BackoffResult calculateBackoff(String blockedReason, LoadSnapshot loadSnapshot) {
        int baseBackoff = getBaseBackoff(blockedReason);
        double loadMultiplier = getLoadMultiplier(loadSnapshot.level());
        double trendMultiplier = 1.0;

        int backoffSeconds = (int) (baseBackoff * loadMultiplier * trendMultiplier);
        backoffSeconds = clampWithJitter(backoffSeconds);

        String suggestion = String.format("系统繁忙，建议在 %s 后重试", 
                Instant.now().plusSeconds(backoffSeconds));

        return BackoffResult.builder()
                .retryAfterSeconds(backoffSeconds)
                .retryAfterTime(Instant.now().plusSeconds(backoffSeconds))
                .reason(blockedReason)
                .suggestion(suggestion)
                .build();
    }

    private int getBaseBackoff(String reason) {
        if (reason == null) {
            return BASE_BACKOFF_QPS;
        }
        return switch (reason.toUpperCase()) {
            case "FLOW_QPS" -> BASE_BACKOFF_QPS;
            case "FLOW_CONCURRENCY" -> BASE_BACKOFF_CONCURRENCY;
            case "DEGRADE" -> BASE_BACKOFF_DEGRADE;
            case "SYSTEM" -> BASE_BACKOFF_SYSTEM;
            default -> BASE_BACKOFF_QPS;
        };
    }

    private double getLoadMultiplier(LoadLevel level) {
        return switch (level) {
            case LOW -> 0.5;
            case NORMAL -> 1.0;
            case HIGH -> 2.0;
            case CRITICAL -> 4.0;
        };
    }

    private int clampWithJitter(int value) {
        int clamped = Math.max(MIN_BACKOFF, Math.min(MAX_BACKOFF, value));
        double jitter = ThreadLocalRandom.current().nextDouble(-0.1, 0.1);
        return (int) (clamped * (1 + jitter));
    }
}
