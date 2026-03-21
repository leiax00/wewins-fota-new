package com.wewins.fota.task.domain.task.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.entity.BaseEntity;
import com.wewins.fota.task.domain.task.model.enums.TaskStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 异步任务实体。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("async_task")
public class AsyncTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 业务类型
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 业务 ID
     */
    @TableField("biz_id")
    private String bizId;

    /**
     * 任务阶段
     */
    @TableField("stage")
    private TaskStage stage;

    /**
     * 进度百分比（0-100）
     */
    @TableField("percent")
    private Integer percent;

    /**
     * 进度消息
     */
    @TableField("message")
    private String message;

    /**
     * 错误信息
     */
    @TableField("error_msg")
    private String errorMsg;

}
