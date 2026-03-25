package com.wewins.fota.task.application.task;

import com.wewins.fota.task.application.service.SseEmitterManager;
import com.wewins.fota.task.domain.task.model.entity.AsyncTask;
import com.wewins.fota.task.domain.task.model.enums.TaskStage;
import com.wewins.fota.task.domain.task.repository.AsyncTaskRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 任务执行上下文。
 * <p>
 * 提供进度回调能力，用于异步任务执行过程中更新进度并推送 SSE 消息。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Slf4j
public class TaskContext {

    private final Long taskId;
    private final SseEmitterManager sseEmitterManager;
    private final AsyncTaskRepository taskRepository;
    private TaskStage currentStage;
    private Integer currentPercent;
    private String currentMessage;

    public TaskContext(Long taskId, SseEmitterManager sseEmitterManager, AsyncTaskRepository taskRepository) {
        this.taskId = taskId;
        this.sseEmitterManager = sseEmitterManager;
        this.taskRepository = taskRepository;
    }

    /**
     * 更新任务进度
     *
     * @param stage   任务阶段
     * @param percent 进度百分比（0-100）
     * @param message 进度消息
     */
    public void updateProgress(TaskStage stage, int percent, String message) {
        this.currentStage = stage;
        this.currentPercent = percent;
        this.currentMessage = message;

        // 持久化到数据库
        Optional<AsyncTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            AsyncTask task = taskOpt.get();
            task.setStage(stage);
            task.setPercent(percent);
            task.setMessage(message);
            taskRepository.updateProgress(task);
        }

        // 推送 SSE 消息
        sseEmitterManager.pushProgress(taskId.toString(), stage, percent, message);

        log.debug("Task {} progress updated: stage={}, percent={}%, message={}",
                taskId, stage, percent, message);
    }

    /**
     * 获取当前任务 ID（Long 类型）
     *
     * @return 任务 ID
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 获取当前任务阶段
     *
     * @return 任务阶段
     */
    public TaskStage getCurrentStage() {
        return currentStage;
    }

    /**
     * 获取当前进度百分比
     *
     * @return 进度百分比
     */
    public Integer getCurrentPercent() {
        return currentPercent;
    }

    /**
     * 获取当前进度消息
     *
     * @return 进度消息
     */
    public String getCurrentMessage() {
        return currentMessage;
    }
}
