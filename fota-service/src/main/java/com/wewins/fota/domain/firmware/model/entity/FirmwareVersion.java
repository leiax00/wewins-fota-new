package com.wewins.fota.domain.firmware.model.entity;

import com.wewins.fota.domain.base.entity.DomainEntity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

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
public class FirmwareVersion extends DomainEntity {

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 版本号（如 1.0.0）
     */
    private String version;

    /**
     * 内部版本号（build tag）
     * <p>
     * 用于与 version 字段组合唯一确定固件版本
     * 由于历史设计缺陷，version 号可能在不同构建中重复
     * 通过 internal_version 可精确区分不同的构建版本
     * </p>
     * <p>
     * 示例：ASR_YEMEN_M476_V11_B03_Build02
     * </p>
     */
    private String internalVersion;

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
     */
    private Map<String, String> tags;

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
     *     "zh-CN": 修复蓝牙断连问题",
     *     "en-US": "Fix Bluetooth disconnection"
     *   },
     *   "changelog": "xxxxxxxxxx",
     *   "part": "main"
     * }
     * </pre>
     * </p>
     */
    private Map<String, Object> meta;

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
    private LocalDateTime deletedAt;
}
