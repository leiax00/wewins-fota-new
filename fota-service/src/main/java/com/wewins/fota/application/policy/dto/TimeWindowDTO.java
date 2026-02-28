package com.wewins.fota.application.policy.dto;

import com.wewins.fota.domain.policy.enums.TimeWindowType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 时间窗口 DTO
 * <p>
 * 使用 LocalDateTime 类型，利用 Jackson 自动时区转换
 * </p>
 * <p>
 * <strong>时区处理</strong>：
 * <ul>
 *   <li>数据库存储：UTC 时间</li>
 *   <li>前端发送：客户端本地时间</li>
 *   <li>前端接收：客户端本地时间（Jackson 自动转换）</li>
 * </ul>
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
 * 当 {@code type} 为 {@code UNLIMITED} 时，{@code startAt} 和 {@code endAt} 应为 null
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
     * 开始时间
     * <p>
     * Jackson 会自动根据 Time-Zone header 进行时区转换
     * </p>
     * <p>
     * 当 {@code type} 为 {@code UNLIMITED} 时，此字段应为 null
     * </p>
     */
    private LocalDateTime startAt;

    /**
     * 结束时间
     * <p>
     * Jackson 会自动根据 Time-Zone header 进行时区转换
     * </p>
     * <p>
     * 当 {@code type} 为 {@code UNLIMITED} 时，此字段应为 null
     * </p>
     */
    private LocalDateTime endAt;

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
     *   <li>当 type 为 UNLIMITED 时，startAt 和 endAt 必须为 null</li>
     *   <li>当 type 为 RANGE 或 DAILY 时，startAt 和 endAt 不能为 null</li>
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
            // UNLIMITED 模式下，时间字段必须为 null
            if (startAt != null) {
                return "timeWindow.startAt 在 UNLIMITED 模式下应为 null";
            }
            if (endAt != null) {
                return "timeWindow.endAt 在 UNLIMITED 模式下应为 null";
            }
        } else {
            // RANGE 和 DAILY 模式下，时间字段不能为空
            if (startAt == null) {
                return "timeWindow.startAt 不能为空";
            }
            if (endAt == null) {
                return "timeWindow.endAt 不能为空";
            }
        }

        return null; // 验证通过
    }
}
