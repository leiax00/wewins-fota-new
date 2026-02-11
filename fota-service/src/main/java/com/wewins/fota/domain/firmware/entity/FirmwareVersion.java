package com.wewins.fota.domain.firmware.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 固件版本实体类
 * <p>
 * 对应数据库表：firmware_versions
 * 存储所有固件版本的信息和文件元数据
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("firmware_versions")
public class FirmwareVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 固件版本唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 版本号（如 1.0.0）
     */
    private String version;

    /**
     * 固件文件下载地址
     */
    private String fileUrl;

    /**
     * 固件文件大小（字节）
     */
    private Long fileSize;

    /**
     * MD5 校验和
     */
    private String md5;

    /**
     * SHA-256 校验和
     */
    private String sha256;

    /**
     * 版本标签（JSONB 对象，KV 结构）
     * <p>
     * 支持灵活的键值对标签，可存储丰富的元数据
     * </p>
     * <p>
     * 示例：
     * <pre>
     * {
     *   "stability": "stable",
     *   "priority": "high",
     *   "category": "security-fix",
     *   "verified_by": "security-team",
     *   "verified_at": "2025-02-05T10:30:00",
     *   "rollback_available": true
     * }
     * </pre>
     * </p>
     * <p>
     * 用于升级策略过滤和版本分类
     * </p>
     * <p>
     * 通过 JsonNodeTypeHandler 自动处理 JsonNode 与 JSONB 之间的转换
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private JsonNode tags;

    /**
     * 扩展元数据（JSONB）
     * <p>
     * 包含多语言描述、changelog、扩展字段等
     * </p>
     * <p>
     * 示例结构：
     * <pre>
     * {
     *   "i18n": {
     *     "zh-CN": {
     *       "description": "修复蓝牙断连问题",
     *       "changelog": "1. 修复蓝牙断连\n2. 优化功耗"
     *     },
     *     "en-US": {
     *       "description": "Fix Bluetooth disconnection",
     *       "changelog": "1. Fix Bluetooth drop\n2. Optimize power"
     *     }
     *   },
     *   "notes": {
     *     "min_app_version": "2.3.0",
     *     "requires_reboot": true
     *   }
     * }
     * </pre>
     * </p>
     */
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private JsonNode meta;

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
