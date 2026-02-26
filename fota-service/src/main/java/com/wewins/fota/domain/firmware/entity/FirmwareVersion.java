package com.wewins.fota.domain.firmware.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wewins.fota.database.entity.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
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
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "firmware_versions", autoResultMap = true)
public class FirmwareVersion extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 版本号（如 1.0.0）
     */
    private String version;

    /**
     * 固件文件下载地址（可空，支持无包版本）
     * <p>
     * 存储对象存储的 objectKey（如：fota/fw/2/a1b2c3d4e5f6.zip）
     * </p>
     */
    private String fileUrl;

    /**
     * 固件原始文件名（可空）
     * <p>
     * 保存用户上传时的原始文件名，便于追溯和下载时使用。
     * package_status=READY 时应有值。
     * </p>
     * <p>
     * 示例：firmware-v1.0.0.bin, device-update.tar.gz
     * </p>
     */
    private String fileName;

    /**
     * 固件文件大小（字节，可空）
     */
    private Long fileSize;

    /**
     * MD5 校验和（可空）
     */
    private String md5;

    /**
     * SHA-256 校验和（可空）
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
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
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
    @TableField(typeHandler = com.wewins.fota.database.handler.JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode meta;

    /**
     * 固件包状态
     * <p>
     * 可选值：
     * <ul>
     *   <li>NONE - 无固件包（占位版本号）</li>
     *   <li>UPLOADED - 已上传临时文件</li>
     *   <li>READY - 已转存到对象存储，可下载</li>
     *   <li>FAILED - 上传或转存失败</li>
     * </ul>
     * </p>
     */
    private String packageStatus;

    /**
     * 固件包上传时间
     * <p>
     * 记录最近一次上传完成时间（临时上传成功或最终转存成功时更新）
     * </p>
     */
    private LocalDateTime packageUploadedAt;

    /**
     * 软删除时间（逻辑删除）
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
