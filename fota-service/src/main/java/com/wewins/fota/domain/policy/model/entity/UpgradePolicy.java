package com.wewins.fota.domain.policy.model.entity;

import com.wewins.fota.domain.base.entity.DomainEntity;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.model.enums.TriggerMode;
import com.wewins.fota.domain.policy.model.vo.PolicyTimeWindow;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

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
public class UpgradePolicy extends DomainEntity {

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
     * 允许升级的源版本 ID 列表（JSONB 数组）
     * <p>
     * 指定哪些源版本（通过 ID）可以升级到此目标版本
     * </p>
     * <p>
     * 示例：
     * <pre>
     * [10, 11, 12]
     * </pre>
     * </p>
     * <p>
     * 业务规则：必须非空，至少包含一个版本 ID
     * </p>
     */
    private Set<Long> sourceVersions;

    /**
     * 优先级（数值越大优先级越高）
     */
    private Integer priority;

    /**
     * 灰度比例（0-100）
     */
    private Integer grayRate;

    /**
     * 策略状态
     */
    private PolicyStatus status;

    /**
     * 触发模式
     */
    private TriggerMode triggerMode;

    /**
     * 目标设备模式
     * <p>
     * 可选值：
     * </p>
     * <ul>
     *   <li>ALL：全量设备</li>
     *   <li>DEVICE_IDS：指定设备IMEI列表</li>
     *   <li>DEVICE_BATCHES：指定设备批次列表</li>
     *   <li>DEVICE_TAGS：按标签筛选（AND 逻辑）</li>
     * </ul>
     */
    private String targetMode;

    /**
     * 指定设备IMEI列表（JSONB 数组）
     * <p>
     * 当 targetMode = DEVICE_IDS 时使用
     * </p>
     * <p>
     * 示例：
     * <pre>
     * ["869123456789012", "869123456789013", "869123456789014"]
     * </pre>
     * </p>
     */
    private Set<String> targetImeis;

    /**
     * 指定设备批次ID列表（JSONB 数组）
     * <p>
     * 当 targetMode = DEVICE_BATCHES 时使用
     * </p>
     * <p>
     * 示例：
     * <pre>
     * [1, 2, 3]
     * </pre>
     * </p>
     */
    private Set<Long> targetDeviceBatchIds;

    /**
     * 设备标签过滤条件（JSONB 对象）
     * <p>
     * 当 targetMode = DEVICE_TAGS 时使用，AND 逻辑
     * </p>
     * <p>
     * 示例：
     * <pre>
     * {
     *   "env": "test",
     *   "region": "CN"
     * }
     * </pre>
     * </p>
     */
    private Map<String, Object> targetDeviceTags;

    /**
     * 时间窗口配置（JSONB）
     * <p>
     * 统一使用 ISO8601 UTC 时间戳
     * </p>
     * <p>
     * 支持两种类型：
     * </p>
     * <ul>
     *   <li>RANGE：固定范围（可跨越多天）</li>
     *   <li>DAILY：每日周期（约定不超过 24 小时）</li>
     * </ul>
     * <p>
     * 示例1 - RANGE 固定范围：
     * <pre>
     * {
     *   "type": "RANGE",
     *   "startAt": "2026-02-01T00:00:00Z",
     *   "endAt": "2026-02-10T23:59:59Z"
     * }
     * </pre>
     * </p>
     * <p>
     * 示例2 - DAILY 每日周期：
     * <pre>
     * {
     *   "type": "DAILY",
     *   "startAt": "2026-02-01T02:00:00Z",
     *   "endAt": "2026-02-01T06:00:00Z"
     * }
     * </pre>
     * </p>
     * <p>
     * 时间区间语义：左闭右开 [startAt, endAt)
     * </p>
     */
    private PolicyTimeWindow timeWindow;

    /**
     * 软删除时间（逻辑删除）
     */
    private LocalDateTime deletedAt;
}
