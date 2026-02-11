package com.wewins.fota.domain.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备导入批次实体类
 * <p>
 * 对应数据库表：device_import_batches
 * 支持批次状态管理和导入统计
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("device_import_batches")
public class DeviceImportBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 批次唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 批次名称（用户自定义或自动生成）
     * <p>
     * 示例：
     * - 用户自定义：20250205_首批测试设备
     * - 自动生成：20250205143000
     * </p>
     */
    private String batchName;

    /**
     * 批次状态
     * <p>
     * 可选值：
     * - IMPORTING：导入中
     * - SUCCESS：全部成功
     * - FAILED：全部失败
     * - PARTIAL：部分成功
     * </p>
     */
    private String status;

    /**
     * 导入文件路径或标识
     */
    private String sourceFile;

    /**
     * 导入总数量
     */
    private Integer totalCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failedCount;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 开始导入时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束导入时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 创建人用户ID（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 更新时间（插入和更新时自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 更新人用户ID（插入和更新时自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 软删除时间（逻辑删除）
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
