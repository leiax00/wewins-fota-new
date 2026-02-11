package com.wewins.fota.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基础请求 VO
 * <p>
 * 提供通用的分页、排序、过滤功能
 * </p>
 * <p>
 * 继承自 BaseReqVO，保持向后兼容
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * {
 *   "page": 1,
 *   "size": 20,
 *   "sortingFields": [
 *     {"field": "username", "order": "ASC"},
 *     {"field": "createdAt", "order": "DESC"}
 *   ],
 *   "filters": {
 *     "status": {"operator": "EQ", "value": "active"},
 *     "createdAt": {"operator": "BETWEEN", "value": "2025-01-01", "endValue": "2025-12-31"}
 *   }
 * }
 * }</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseRequestVo extends BaseReqVO {
    // 保持向后兼容，所有功能已在 BaseReqVO 中实现
}
