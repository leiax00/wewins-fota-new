package com.wewins.fota.task.application.dto;

import com.wewins.fota.task.domain.task.model.enums.TaskStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务状态响应。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResp {

    /**
     * 任务 ID
     */
    private String id;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务 ID
     */
    private String bizId;

    /**
     * 任务阶段
     */
    private TaskStage stage;

    /**
     * 进度百分比（0-100）
     */
    private Integer percent;

    /**
     * 进度消息
     */
    private String message;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
