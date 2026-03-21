package com.wewins.fota.task.application.service;

import com.wewins.fota.task.application.dto.TaskCreateCmd;
import com.wewins.fota.task.application.dto.TaskStatusResp;
import com.wewins.fota.task.application.task.TaskContext;
import com.wewins.fota.task.domain.task.model.entity.AsyncTask;
import com.wewins.fota.task.domain.task.model.enums.TaskStage;
import com.wewins.fota.task.domain.task.repository.AsyncTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 异步任务应用服务。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Slf4j
@Service
public class AsyncTaskService {

    private final AsyncTaskRepository taskRepository;
    private final SseEmitterManager sseEmitterManager;

    public AsyncTaskService(
            @Qualifier("asyncTaskRepositoryImpl") AsyncTaskRepository taskRepository,
            SseEmitterManager sseEmitterManager) {
        this.taskRepository = taskRepository;
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * 创建任务
     *
     * @param cmd 创建命令
     * @return 任务 ID（Long 类型）
     */
    public Long createTask(TaskCreateCmd cmd) {
        AsyncTask task = AsyncTask.builder()
                .bizType(cmd.getBizType())
                .bizId(cmd.getBizId())
                .stage(TaskStage.INIT)
                .percent(0)
                .message("任务已创建")
                .build();

        taskRepository.save(task);
        log.info("Task created: id={}, bizType={}, bizId={}", task.getId(), task.getBizType(), task.getBizId());

        return task.getId();
    }

    /**
     * 执行异步任务
     *
     * @param taskId   任务 ID（Long 类型）
     * @param executor 任务执行器
     */
    @Async("fotaTaskExecutor")
    public void executeTask(Long taskId, Consumer<TaskContext> executor) {
        TaskContext context = new TaskContext(taskId, sseEmitterManager, taskRepository);

        try {
            // 更新状态为处理中
            updateTaskStatus(taskId, TaskStage.PROCESSING, 0, "任务开始执行");

            // 执行任务
            executor.accept(context);

            // 更新状态为完成
            updateTaskStatus(taskId, TaskStage.COMPLETED, 100, "任务执行完成");

        } catch (Exception e) {
            log.error("Task execution failed: {}", taskId, e);
            updateTaskStatus(taskId, TaskStage.FAILED, 0, "任务执行失败: " + e.getMessage());
        }
    }

    /**
     * 订阅任务进度
     *
     * @param taskId 任务 ID（Long 类型）
     * @return SseEmitter
     */
    public SseEmitter subscribe(Long taskId) {
        // 验证任务是否存在
        Optional<AsyncTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        return sseEmitterManager.subscribe(taskId.toString());
    }

    /**
     * 订阅任务进度（支持 String 类型 ID）
     *
     * @param taskId 任务 ID（String 类型）
     * @return SseEmitter
     */
    public SseEmitter subscribeByString(String taskId) {
        long parsedId;
        try {
            parsedId = Long.parseLong(taskId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid task ID: " + taskId);
        }
        return subscribe(parsedId);
    }

    /**
     * 获取任务状态
     *
     * @param taskId 任务 ID（Long 类型）
     * @return 任务状态响应
     */
    public TaskStatusResp getTaskStatus(Long taskId) {
        Optional<AsyncTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        AsyncTask task = taskOpt.get();
        return TaskStatusResp.builder()
                .id(task.getId().toString())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .stage(task.getStage())
                .percent(task.getPercent())
                .message(task.getMessage())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    /**
     * 获取任务状态（支持 String 类型 ID）
     *
     * @param taskId 任务 ID（String 类型）
     * @return 任务状态响应
     */
    public TaskStatusResp getTaskStatusByString(String taskId) {
        try {
            return getTaskStatus(Long.parseLong(taskId));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid task ID: " + taskId);
        }
    }

    /**
     * 更新任务状态
     *
     * @param taskId  任务 ID（Long 类型）
     * @param stage   任务阶段
     * @param percent 进度百分比
     * @param message 进度消息
     */
    private void updateTaskStatus(Long taskId, TaskStage stage, int percent, String message) {
        Optional<AsyncTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return;
        }

        AsyncTask task = taskOpt.get();
        task.setStage(stage);
        task.setPercent(percent);
        task.setMessage(message);

        if (stage == TaskStage.FAILED) {
            task.setErrorMsg(message);
        }

        taskRepository.updateProgress(task);

        // 推送 SSE 消息
        sseEmitterManager.pushProgress(taskId.toString(), stage, percent, message);
    }

    /**
     * 取消任务
     *
     * @param taskId 任务 ID（Long 类型）
     */
    public void cancelTask(Long taskId) {
        updateTaskStatus(taskId, TaskStage.CANCELLED, 0, "任务已取消");
        sseEmitterManager.unsubscribe(taskId.toString());
    }
}
