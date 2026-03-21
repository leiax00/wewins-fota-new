package com.wewins.fota.task.domain.task.model.enums;

/**
 * 任务阶段枚举。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
public enum TaskStage {

    /**
     * 初始化
     */
    INIT,

    /**
     * 处理中
     */
    PROCESSING,

    /**
     * 完成
     */
    COMPLETED,

    /**
     * 失败
     */
    FAILED,

    /**
     * 取消
     */
    CANCELLED
}
