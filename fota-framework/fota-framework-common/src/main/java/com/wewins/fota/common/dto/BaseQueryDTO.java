package com.wewins.fota.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础 Query DTO
 * <p>
 * 提供通用分页、排序与过滤能力
 * </p>
 * <p>
 * 作为所有查询 DTO 的基类，提供：
 * <ul>
 *   <li>分页参数（继承自 PageParam）</li>
 *   <li>排序字段列表</li>
 *   <li>通用过滤器 Map</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseQueryDTO extends PageParam {

    /**
     * 排序字段列表
     * <p>
     * 按顺序应用多个排序规则，先应用的排序优先级更高
     * </p>
     */
    private List<SortingField> sortingFields = new ArrayList<>();

    /**
     * 通用过滤器
     * <p>
     * 支持丰富的查询条件，例如：
     * <ul>
     *   <li>status: { "operator": "EQ", "value": "enabled" }</li>
     *   <li>createdAt: { "operator": "BETWEEN", "value": "2025-01-01", "endValue": "2025-12-31" }</li>
     *   <li>username: { "operator": "LIKE", "value": "admin" }</li>
     * </ul>
     * </p>
     */
    private Map<String, FilterCondition> filters = new HashMap<>();

    /**
     * 获取过滤器值（简化访问）
     * <p>
     * 快捷获取过滤器值，默认假设为 EQ 操作符
     * </p>
     *
     * @param key 过滤器键
     * @return 过滤器值，不存在时返回 null
     */
    public String getFilterValue(String key) {
        FilterCondition condition = filters.get(key);
        if (condition != null && condition.getValue() != null) {
            return condition.getValue().toString();
        }
        return null;
    }

    /**
     * 获取过滤器条件
     *
     * @param key 过滤器键
     * @return 过滤器条件，不存在时返回 null
     */
    public FilterCondition getFilterCondition(String key) {
        return filters.get(key);
    }

    /**
     * 设置过滤器条件
     *
     * @param key      过滤器键
     * @param condition 过滤器条件
     */
    public void setFilter(String key, FilterCondition condition) {
        if (condition != null) {
            filters.put(key, condition);
        }
    }

    /**
     * 设置过滤器值（简化设置，默认 EQ 操作符）
     *
     * @param key   过滤器键
     * @param value 值
     */
    public void setFilterValue(String key, Object value) {
        if (value != null) {
            filters.put(key, FilterCondition.eq(value));
        }
    }
}
