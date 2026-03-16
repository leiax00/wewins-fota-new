package com.wewins.fota.infra.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 将 Logback 日志事件桥接到 SSE 广播器
 * <p>
 * 注意：由于 Appender 由 Logback 管理，不是 Spring Bean，
 * 需要通过静态引用访问 {@link SseLogBroadcaster}
 */
@Slf4j
public class LogbackSseAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final int MAX_MESSAGE_LENGTH = 10000;
    private static final String LINE_SEPARATOR = System.lineSeparator();

    @Override
    protected void append(ILoggingEvent event) {
        SseLogBroadcaster broadcaster = SseLogBroadcasterHolder.getInstance();
        if (broadcaster == null) {
            return;
        }

        try {
            LogEvent logEvent = LogEvent.of(
                    event.getTimeStamp(),
                    event.getLevel() != null ? event.getLevel().toString() : "INFO",
                    sanitizeLoggerName(event.getLoggerName()),
                    buildMessage(event),
                    event.getThreadName()
            );
            broadcaster.broadcast(logEvent);
        } catch (Exception e) {
            // 防止日志错误造成级联故障
            addError("广播 SSE 日志事件失败", e);
        }
    }

    /**
     * 构建日志消息，包含异常堆栈
     */
    private String buildMessage(ILoggingEvent event) {
        String message = event.getFormattedMessage();

        // 处理异常堆栈
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy == null) {
            return truncateMessage(message);
        }

        StringBuilder builder = new StringBuilder();
        if (message != null && !message.isBlank()) {
            builder.append(message);
            if (!message.endsWith(LINE_SEPARATOR)) {
                builder.append(LINE_SEPARATOR);
            }
        }

        // 添加堆栈跟踪
        String stackTrace = ThrowableProxyUtil.asString(throwableProxy);
        builder.append(stackTrace);

        return truncateMessage(builder.toString());
    }

    /**
     * 截断过长的消息，防止内存问题
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH) + "... [truncated]";
    }

    /**
     * 简化日志记录器名称，去掉包名前缀
     */
    private String sanitizeLoggerName(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return "";
        }
        // 保留最后的类名，包名只保留首字母
        int lastDot = loggerName.lastIndexOf('.');
        if (lastDot < 0) {
            return loggerName;
        }
        String packageName = loggerName.substring(0, lastDot);
        String className = loggerName.substring(lastDot + 1);
        String shortenedPackage = shortenPackageName(packageName);
        return shortenedPackage.isEmpty() ? className : shortenedPackage + "." + className;
    }

    /**
     * 简化包名
     */
    private String shortenPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        String[] parts = packageName.split("\\.");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                result.append(parts[i].charAt(0));
                result.append('.');
            }
        }
        if (parts.length > 0) {
            result.append(parts[parts.length - 1]);
        }
        return result.toString();
    }

    @Override
    public void start() {
        super.start();
        addInfo("Logback SSE appender 已启动");
    }

    @Override
    public void stop() {
        super.stop();
        addInfo("Logback SSE appender 已停止");
    }

    /**
     * SSE 广播器持有者
     * <p>
     * 用于在 Logback Appender（非 Spring Bean）中访问 Spring 管理的 {@link SseLogBroadcaster}
     */
    public static final class SseLogBroadcasterHolder {

        /**
         * -- SETTER --
         *  设置广播器实例（由 Spring 容器调用）
         * -- GETTER --
         *  获取广播器实例

         */
        @Getter
        @Setter
        private static volatile SseLogBroadcaster instance;

        /**
         * 清除广播器实例
         */
        public static void clearInstance() {
            instance = null;
        }

        private SseLogBroadcasterHolder() {
            // 防止实例化
        }
    }
}
