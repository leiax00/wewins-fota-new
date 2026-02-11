package com.wewins.fota.common.dto;

import lombok.Data;

/**
 * 基础分页参数
 * <p>
 * 提供通用的分页查询参数，包含页码、每页大小和关键词搜索
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
@Data
public class PageParam {

    /**
     * 页码（从 1 开始）
     */
    private int page = 1;

    /**
     * 每页大小
     */
    private int size = 20;

    /**
     * 关键词搜索
     */
    private String keyword;

    /**
     * 校验并修正分页参数
     * <p>
     * 确保参数在合理范围内：
     * <ul>
     *   <li>page >= 1</li>
     *   <li>1 <= size <= 500</li>
     * </ul>
     * </p>
     */
    public void validate() {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 500) {
            size = 500;
        }
    }
}
