package com.wewins.fota.application.policy.dto;

import com.wewins.fota.domain.policy.enums.TimeWindowType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 时间窗口 DTO
 * <p>
 * 统一使用 ISO8601 UTC 时间戳，避免时区问题
 * </p>
 * <p>
 * 支持三种类型：
 * <ul>
 *   <li>{@code UNLIMITED}: 不限制，随时可以触发升级</li>
 *   <li>{@code RANGE}: 固定范围，可跨越多天</li>
 *   <li>{@code DAILY}: 每日周期，约定不超过 24 小时</li>
 * </ul>
 * </p>
 * <p>
 * 时间区间语义：左闭右开 {@code [startAt, endAt)}
 * </p>
 * <p>
 * 当 {@code type} 为 {@code UNLIMITED} 时，{@code startAt} 和 {@code endAt} 应为空字符串
 * </p>
 */
@Data
public class TimeWindowDTO {

    /**
     * 类型：UNLIMITED（不限制）/ RANGE（固定范围）/ DAILY（每日周期）
     */
    @NotBlank(message = "timeWindow.type 不能为空")
    private String type;

    /**
     * 开始时间（ISO8601 UTC）
     * <p>
     * 格式示例：{@code 2026-02-26T00:00:00Z}
     * </p>
     * <p>
     * 当 {@code type} 为 {@code UNLIMITED} 时，此字段应为空字符串
     * </p>
     */
    private String startAt;

    /**
     * 结束时间（ISO8601 UTC）
     * <p>
     * 格式示例：{@code 2026-02-26T23:59:59Z}
     * </p>
     * <p>
     * 当 {@code type} 为 {@code UNLIMITED} 时，此字段应为空字符串
     * </p>
     */
    private String endAt;

    /**
     * 获取时间窗口类型枚举
     *
     * @return 时间窗口类型枚举
     */
    public TimeWindowType getTypeEnum() {
        return TimeWindowType.of(type);
    }

    /**
     * 验证时间窗口数据的有效性
     * <p>
     * 业务规则：
     * <ul>
     *   <li>type 不能为空</li>
     *   <li>当 type 为 UNLIMITED 时，startAt 和 endAt 必须为空字符串</li>
     *   <li>当 type 为 RANGE 或 DAILY 时，startAt 和 endAt 不能为空</li>
     * </ul>
     * </p>
     *
     * @return 验证错误信息，如果验证通过则返回 null
     */
    public String validate() {
        if (type == null || type.isBlank()) {
            return "timeWindow.type 不能为空";
        }

        TimeWindowType typeEnum = getTypeEnum();
        if (typeEnum == null) {
            return "timeWindow.type 值无效，支持 UNLIMITED/RANGE/DAILY";
        }

        if (typeEnum == TimeWindowType.UNLIMITED) {
            // UNLIMITED 模式下，时间字段必须为空
            if (startAt != null && !startAt.isBlank()) {
                return "timeWindow.startAt 在 UNLIMITED 模式下应为空字符串";
            }
            if (endAt != null && !endAt.isBlank()) {
                return "timeWindow.endAt 在 UNLIMITED 模式下应为空字符串";
            }
        } else {
            // RANGE 和 DAILY 模式下，时间字段不能为空
            if (startAt == null || startAt.isBlank()) {
                return "timeWindow.startAt 不能为空";
            }
            if (endAt == null || endAt.isBlank()) {
                return "timeWindow.endAt 不能为空";
            }
        }

        return null; // 验证通过
    }
}
