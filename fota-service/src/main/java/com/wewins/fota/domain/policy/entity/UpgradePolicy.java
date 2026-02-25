package com.wewins.fota.domain.policy.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wewins.fota.database.entity.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 升级策略实体类
 * <p>
 * 对应数据库表：upgrade_policies
 * 存储固件升级策略和配置
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "upgrade_policies", autoResultMap = true)
public class UpgradePolicy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 策略名称
     */
    private String name;

    /**
     * 策略备注
     */
    private String remark;

    /**
     * 目标固件版本 ID
     */
    private Long targetVersionId;

    /**
     * 允许升级的源版本列表（JSONB 数组）
     * <p>
     * 指定哪些源版本可以升级到此目标版本
     * </p>
     * <p>
     * 示例：
     * <pre>
     * ["1.0.0", "1.0.1", "1.0.2-beta"]
     * </pre>
     * </p>
     * <p>
     * 如果为空，表示不限制源版本
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode sourceVersions;

    /**
     * 优先级（数值越大优先级越高）
     */
    private Integer priority;

    /**
     * 灰度比例（0-100）
     */
    private Integer grayRate;

    /**
     * 计划时间
     */
    private LocalDateTime planTime;

    /**
     * 策略状态（ACTIVE/PAUSED/EXPIRED）
     */
    private String status;

    /**
     * 触发模式
     * <p>
     * 可选值：
     * </p>
     * <ul>
     *   <li>AUTO：系统自动触发推送</li>
     *   <li>MANUAL：人工确认触发推送</li>
     * </ul>
     */
    private String triggerMode;

    /**
     * 指定设备ID列表（JSONB 数组）
     * <p>
     * 当设备数量 ≤10 个时，使用此字段直接指定设备
     * </p>
     * <p>
     * 示例：
     * <pre>
     * [1001, 1002, 1003]
     * </pre>
     * </p>
     * <p>
     * 如果为空，表示不限制设备ID
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode targetDeviceIds;

    /**
     * 设备标签过滤条件（JSONB 对象）
     * <p>
     * 当设备数量 >10 个时，使用此字段通过标签筛选设备
     * </p>
     * <p>
     * 示例：
     * <pre>
     * {
     *   "all": ["CN", "VIP"],
     *   "any": ["beta", "pilot"],
     *   "none": ["blocked"]
     * }
     * </pre>
     * </p>
     * <p>
     * 如果为空，表示不限制设备标签
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode targetDeviceTags;

    /**
     * 时间窗口配置（JSONB）
     * <p>
     * 支持两种类型：
     * </p>
     * <ul>
     *   <li>固定日期范围（type: range）</li>
     *   <li>每天固定时间段（type: daily）</li>
     * </ul>
     * <p>
     * 示例1 - 固定日期范围：
     * <pre>
     * {
     *   "type": "range",
     *   "start_at": "2025-02-01T00:00:00+08:00",
     *   "end_at": "2025-02-10T23:59:59+08:00"
     * }
     * </pre>
     * </p>
     * <p>
     * 示例2 - 每天固定时间段：
     * <pre>
     * {
     *   "type": "daily",
     *   "timezone": "Asia/Shanghai",
     *   "start_time": "02:00",
     *   "end_time": "06:00"
     * }
     * </pre>
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode timeWindow;

    /**
     * 软删除时间（逻辑删除）
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
