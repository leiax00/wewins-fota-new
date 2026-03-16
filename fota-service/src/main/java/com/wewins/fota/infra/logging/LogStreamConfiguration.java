package com.wewins.fota.infra.logging;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 日志流配置
 * <p>
 * 初始化 Logback SSE Appender 与 Spring 容器之间的桥梁
 */
@Slf4j
@Configuration
public class LogStreamConfiguration {

    public LogStreamConfiguration(SseLogBroadcaster sseLogBroadcaster) {
        LogbackSseAppender.SseLogBroadcasterHolder.setInstance(sseLogBroadcaster);
        log.info("日志流 SSE 广播器已注册到 Logback");
    }

    @PreDestroy
    public void destroy() {
        LogbackSseAppender.SseLogBroadcasterHolder.clearInstance();
        log.info("日志流 SSE 广播器已从 Logback 注销");
    }
}
