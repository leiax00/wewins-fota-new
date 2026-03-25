package com.wewins.fota.task.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务创建命令。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateCmd {

    /**
     * 业务类型
     */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    /**
     * 业务 ID
     */
    @NotBlank(message = "业务ID不能为空")
    private String bizId;
}
