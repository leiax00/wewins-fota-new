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
 * 支持两种类型：
 * <ul>
 *   <li>{@code RANGE}: 固定范围，可跨越多天</li>
 *   <li>{@code DAILY}: 每日周期，约定不超过 24 小时</li>
 * </ul>
 * </p>
 * <p>
 * 时间区间语义：左闭右开 {@code [startAt, endAt)}
 * </p>
 */
@Data
public class TimeWindowDTO {

    /**
     * 类型：RANGE（固定范围）/ DAILY（每日周期）
     */
    @NotBlank(message = "timeWindow.type 不能为空")
    private String type;

    /**
     * 开始时间（ISO8601 UTC）
     * <p>
     * 格式示例：{@code 2026-02-26T00:00:00Z}
     * </p>
     */
    @NotBlank(message = "timeWindow.startAt 不能为空")
    private String startAt;

    /**
     * 结束时间（ISO8601 UTC）
     * <p>
     * 格式示例：{@code 2026-02-26T23:59:59Z}
     * </p>
     */
    @NotBlank(message = "timeWindow.endAt 不能为空")
    private String endAt;

    /**
     * 获取时间窗口类型枚举
     *
     * @return 时间窗口类型枚举
     */
    public TimeWindowType getTypeEnum() {
        return TimeWindowType.of(type);
    }
}
