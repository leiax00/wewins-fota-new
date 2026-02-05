package com.wewins.fota.database.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
