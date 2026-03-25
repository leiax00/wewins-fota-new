package com.wewins.fota.task.adapter.api;

import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.task.application.dto.TaskStatusResp;
import com.wewins.fota.task.application.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 异步任务控制器。
 * <p>
 * 提供任务 SSE 订阅和状态查询接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
@ConditionalOnAppMode("main")
public class TaskController {

    private final AsyncTaskService asyncTaskService;

    /**
     * 订阅任务进度。
     *
     * @param taskId 任务 ID
     * @return SseEmitter
     */
    @GetMapping(value = "/{taskId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public SseEmitter subscribeTaskProgress(@PathVariable Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("任务ID无效");
        }

        if (log.isDebugEnabled()) {
            log.debug("订阅任务进度: taskId={}", taskId);
        }

        return asyncTaskService.subscribe(taskId);
    }

    /**
     * 获取任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    @GetMapping("/{taskId}/status")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<TaskStatusResp> getTaskStatus(@PathVariable Long taskId) {
        if (taskId == null || taskId <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "任务ID无效");
        }

        try {
            var status = asyncTaskService.getTaskStatus(taskId);
            return ApiResponse.success(status);
        } catch (IllegalArgumentException e) {
            log.warn("获取任务状态失败: taskId={}, message={}", taskId, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
