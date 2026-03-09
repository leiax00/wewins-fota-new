package com.wewins.fota.domain.load.model.vo;

import java.time.Instant;

/**
 * 智能退避结果
 * <p>
 * 当请求被限流或熔断时，返回给设备的退避建议
 * </p>
 *
 * @param retryAfterSeconds 重试等待时间（秒）
 * @param retryAfterTime    建议重试的具体时间点
 * @param reason            被拒绝原因（FLOW_QPS/DEGRADE/SYSTEM）
 * @param suggestion        给设备的建议
 */
public record BackoffResult(
        int retryAfterSeconds,
        Instant retryAfterTime,
        String reason,
        String suggestion
) {
    public BackoffResult {
        if (retryAfterTime == null) {
            retryAfterTime = Instant.now().plusSeconds(retryAfterSeconds);
        }
    }

    public static BackoffResultBuilder builder() {
        return new BackoffResultBuilder();
    }

    public static class BackoffResultBuilder {
        private int retryAfterSeconds;
        private Instant retryAfterTime;
        private String reason;
        private String suggestion;

        public BackoffResultBuilder retryAfterSeconds(int retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
            return this;
        }

        public BackoffResultBuilder retryAfterTime(Instant retryAfterTime) {
            this.retryAfterTime = retryAfterTime;
            return this;
        }

        public BackoffResultBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public BackoffResultBuilder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public BackoffResult build() {
            if (retryAfterTime == null) {
                retryAfterTime = Instant.now().plusSeconds(retryAfterSeconds);
            }
            return new BackoffResult(retryAfterSeconds, retryAfterTime, reason, suggestion);
        }
    }
}
