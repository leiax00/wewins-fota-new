package com.wewins.fota.database.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备实体类
 * <p>
 * 对应数据库表：devices
 * 存储所有设备的基本信息和当前状态
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("devices")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备 IMEI 号（唯一）
     */
    private String imei;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 当前固件版本 ID
     */
    private Long currentVersionId;

    /**
     * 设备状态（ACTIVE, INACTIVE, LOST, etc.）
     */
    private String status;

    /**
     * 最后一次在线时间
     */
    private LocalDateTime lastSeenAt;

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
