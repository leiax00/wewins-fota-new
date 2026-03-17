package com.wewins.fota.domain.device.model.entity;

import com.wewins.fota.domain.base.entity.DomainEntity;
import lombok.*;

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
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportBatch extends DomainEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 关联的产品ID（导入时指定的产品）
     * <p>
     * 用于追溯批次属于哪个产品，支持按产品筛选批次。
     * 批次表是导入快照，此字段记录导入时的产品，不随设备变化而变化。
     * </p>
     */
    private Long productId;

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
     * 软删除时间（逻辑删除）
     */
    private LocalDateTime deletedAt;
}
