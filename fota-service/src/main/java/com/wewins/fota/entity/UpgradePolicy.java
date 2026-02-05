package com.wewins.fota.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("upgrade_policies")
public class UpgradePolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 策略唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 策略名称
     */
    private String name;

    /**
     * 策略描述
     */
    private String description;

    /**
     * 目标固件版本 ID
     */
    private Long targetVersionId;

    /**
     * 优先级（数值越大优先级越高）
     */
    private Integer priority;

    /**
     * 灰度比例（0-100）
     */
    private Integer grayRate;

    /**
     * 时间窗口配置（JSONB 格式）
     * <p>
     * 示例：
     * {
     *   "startTime": "00:00",
     *   "endTime": "06:00",
     *   "timezone": "Asia/Shanghai"
     * }
     * </p>
     */
    private String timeWindow;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（插入和更新时自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
