package com.wewins.fota.task.application.service;

import com.wewins.fota.task.config.TaskProperties;
import com.wewins.fota.task.domain.task.model.enums.TaskStage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * SSE 连接管理器。
 * <p>
 * 管理所有活跃的 SSE 连接，支持推送任务进度消息。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Slf4j
public class SseEmitterManager {

    /**
     * 任务 ID -> SseEmitter 映射
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final TaskProperties properties;

    /**
     * 使用信号量限制最大连接数
     */
    private final Semaphore connectionSemaphore;

    /**
     * 使用 Spring 管理的线程池
     */
    private final Executor executor;

    public SseEmitterManager(TaskProperties properties, Executor ssePushExecutor) {
        this.properties = properties;
        this.executor = ssePushExecutor;
        this.connectionSemaphore = new Semaphore(properties.getMaxSseConnections());
    }

    /**
     * 订阅任务进度
     *
     * @param taskId 任务 ID
     * @return SseEmitter
     */
    public SseEmitter subscribe(String taskId) {
        // 原子性获取许可
        if (!connectionSemaphore.tryAcquire()) {
            throw new IllegalStateException("SSE connections exceed maximum: " + properties.getMaxSseConnections());
        }

        try {
            SseEmitter existing = emitters.get(taskId);
            if (existing != null) {
                try {
                    existing.complete();
                } catch (Exception e) {
                    log.debug("Error completing old emitter for task: {}", taskId, e);
                }
            }

            SseEmitter emitter = new SseEmitter(properties.getSseTimeout());
            emitters.put(taskId, emitter);

            emitter.onCompletion(() -> {
                releaseConnection(taskId, emitter);
                log.info("SSE connection completed for task: {}", taskId);
            });

            emitter.onTimeout(() -> {
                releaseConnection(taskId, emitter);
                log.info("SSE connection timeout for task: {}", taskId);
            });

            emitter.onError(e -> {
                releaseConnection(taskId, emitter);
                log.error("SSE connection error for task: {}", taskId, e);
            });

            log.info("SSE subscribed for task: {}, total connections: {}", taskId, emitters.size());
            return emitter;
        } catch (Exception e) {
            // 获取许可后出现异常，需要释放许可
            connectionSemaphore.release();
            throw new IllegalStateException("Failed to create SSE connection", e);
        }
    }

    /**
     * 推送任务进度
     *
     * @param taskId  任务 ID
     * @param stage   任务阶段
     * @param percent 进度百分比
     * @param message 进度消息
     */
    public void pushProgress(String taskId, TaskStage stage, int percent, String message) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return;
        }

        executor.execute(() -> {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .id(taskId)
                        .name("progress")
                        .data(new TaskProgressEvent(taskId, stage, percent, message), MediaType.APPLICATION_JSON);

                emitter.send(event);
            } catch (Exception e) {
                log.error("Failed to push progress for task: {}", taskId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("Failed to complete emitter for task: {}", taskId, ex);
                }
            }
        });
    }

    /**
     * 取消订阅
     *
     * @param taskId 任务 ID
     */
    public void unsubscribe(String taskId) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            connectionSemaphore.release();
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("Failed to complete emitter for task: {}", taskId, e);
            }
        }
    }

    /**
     * 获取当前连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return emitters.size();
    }

    /**
     * 销毁方法，清理资源
     * <p>
     * 注意：线程池由 Spring 管理，此处不关闭
     * </p>
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down SseEmitterManager");
        // 关闭所有连接
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().complete();
            } catch (Exception e) {
                log.debug("Error completing emitter for task: {}", entry.getKey(), e);
            }
        }
        emitters.clear();
    }

    private void releaseConnection(String taskId, SseEmitter emitter) {
        if (emitters.remove(taskId, emitter)) {
            connectionSemaphore.release();
        }
    }

    /**
     * 任务进度事件
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskProgressEvent {
        private String taskId;
        private TaskStage stage;
        private int percent;
        private String message;
    }
}
