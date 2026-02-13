package com.wewins.fota.common.dto;

/**
 * 过滤操作符枚举
 * <p>
 * 定义查询条件的操作符类型，用于 BaseReqVO 的过滤器
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
public enum FilterOperator {

    /**
     * 等于
     */
    EQ,

    /**
     * 不等于
     */
    NE,

    /**
     * 模糊匹配（LIKE）
     */
    LIKE,

    /**
     * 大于
     */
    GT,

    /**
     * 小于
     */
    LT,

    /**
     * 大于等于
     */
    GE,

    /**
     * 小于等于
     */
    LE,

    /**
     * 在...之间（BETWEEN）
     */
    BETWEEN,

    /**
     * 在...之中（IN）
     */
    IN,

    /**
     * 为空（IS NULL）
     */
    IS_NULL,

    /**
     * 不为空（IS NOT NULL）
     */
    NOT_NULL
}
