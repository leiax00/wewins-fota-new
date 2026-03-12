package com.wewins.fota.infra.logging;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日志流事件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {

    /**
     * 日志时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 日志级别
     */
    private String level;

    /**
     * 日志记录器名称
     */
    private String logger;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 线程名称
     */
    private String thread;

    /**
     * 格式化后的时间字符串
     */
    @Builder.Default
    private String formattedTime = "";

    public static LogEvent of(long timestamp, String level, String logger, String message, String thread) {
        String formattedTime = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        return LogEvent.builder()
                .timestamp(timestamp)
                .level(level)
                .logger(logger)
                .message(message)
                .thread(thread)
                .formattedTime(formattedTime)
                .build();
    }
}
