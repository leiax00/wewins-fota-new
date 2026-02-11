package com.wewins.fota.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排序字段
 * <p>
 * 定义单个字段的排序方式
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SortingField {

    /**
     * 字段名
     * <p>
     * 需要在具体实体的字段映射表中定义
     * </p>
     */
    private String field;

    /**
     * 排序方式
     * <p>
     * 支持的值：
     * <ul>
     *   <li>ASC - 升序</li>
     *   <li>DESC - 降序（默认）</li>
     * </ul>
     * </p>
     */
    private String order;
}
