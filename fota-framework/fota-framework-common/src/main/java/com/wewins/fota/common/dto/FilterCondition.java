package com.wewins.fota.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

/**
 * 过滤条件
 * <p>
 * 定义查询条件的操作符和值，用于 BaseRequestVo 的过滤器
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 等于
 * FilterCondition.eq("enabled")
 *
 * // 模糊匹配
 * FilterCondition.like("admin")
 *
 * // 范围查询
 * FilterCondition.between("2025-01-01", "2025-12-31")
 *
 * // IN 查询
 * FilterCondition.in(Arrays.asList("A", "B", "C"))
 * }</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
@Data
@NoArgsConstructor
public class FilterCondition {

    /**
     * 操作符
     */
    private FilterOperator operator;

    /**
     * 值（单值或集合）
     */
    private Object value;

    /**
     * 结束值（用于 BETWEEN 操作符）
     */
    @JsonProperty("endValue")
    private Object endValue;

    /**
     * 两参数构造函数（operator + value）
     *
     * @param operator 操作符
     * @param value    值
     */
    public FilterCondition(FilterOperator operator, Object value) {
        this.operator = operator;
        this.value = value;
    }

    /**
     * 三参数构造函数（operator + value + endValue）
     *
     * @param operator 操作符
     * @param value    开始值
     * @param endValue 结束值
     */
    public FilterCondition(FilterOperator operator, Object value, Object endValue) {
        this.operator = operator;
        this.value = value;
        this.endValue = endValue;
    }

    /**
     * 单参数构造函数（仅 operator）
     *
     * @param operator 操作符
     */
    public FilterCondition(FilterOperator operator) {
        this.operator = operator;
    }

    /**
     * 等于
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition eq(Object value) {
        return new FilterCondition(FilterOperator.EQ, value);
    }

    /**
     * 不等于
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition ne(Object value) {
        return new FilterCondition(FilterOperator.NE, value);
    }

    /**
     * 模糊匹配
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition like(String value) {
        return new FilterCondition(FilterOperator.LIKE, value);
    }

    /**
     * 大于
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition gt(Object value) {
        return new FilterCondition(FilterOperator.GT, value);
    }

    /**
     * 小于
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition lt(Object value) {
        return new FilterCondition(FilterOperator.LT, value);
    }

    /**
     * 大于等于
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition ge(Object value) {
        return new FilterCondition(FilterOperator.GE, value);
    }

    /**
     * 小于等于
     *
     * @param value 值
     * @return 过滤条件
     */
    public static FilterCondition le(Object value) {
        return new FilterCondition(FilterOperator.LE, value);
    }

    /**
     * 在...之间
     *
     * @param start 开始值
     * @param end   结束值
     * @return 过滤条件
     */
    public static FilterCondition between(Object start, Object end) {
        return new FilterCondition(FilterOperator.BETWEEN, start, end);
    }

    /**
     * 在...之中
     *
     * @param values 值集合
     * @return 过滤条件
     */
    @SafeVarargs
    public static FilterCondition in(Object... values) {
        return new FilterCondition(FilterOperator.IN, values);
    }

    /**
     * 在...之中（集合版本）
     *
     * @param values 值集合
     * @return 过滤条件
     */
    public static FilterCondition in(Collection<?> values) {
        return new FilterCondition(FilterOperator.IN, values);
    }

    /**
     * 为空
     *
     * @return 过滤条件
     */
    public static FilterCondition isNull() {
        return new FilterCondition(FilterOperator.IS_NULL);
    }

    /**
     * 不为空
     *
     * @return 过滤条件
     */
    public static FilterCondition notNull() {
        return new FilterCondition(FilterOperator.NOT_NULL);
    }
}
