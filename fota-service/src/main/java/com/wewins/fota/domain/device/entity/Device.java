package com.wewins.fota.domain.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wewins.fota.database.entity.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

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
@TableName(value = "devices", autoResultMap = true)
public class Device extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 设备标签（JSONB 对象，KV 结构）
     * <p>
     * 支持灵活的键值对标签，可存储丰富的元数据
     * </p>
     * <p>
     * 示例：
     * <pre>
     * {
     *   "environment": "测试",
     *   "region": "CN",
     *   "network": "5G",
     *   "user_level": "VIP",
     *   "tester": "张三",
     *   "test_phase": "alpha"
     * }
     * </pre>
     * </p>
     * <p>
     * 使用 JSONB 类型存储，支持高效的 JSON 查询和索引
     * </p>
     * <p>
     * 通过 JsonNodeTypeHandler 自动处理 JsonNode 与 JSONB 之间的转换
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode tags;

    /**
     * 导入批次ID
     * <p>
     * 关联 device_import_batches 表，记录设备导入的批次信息
     * </p>
     */
    private Long importBatchId;

    /**
     * 软删除时间（逻辑删除）
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
