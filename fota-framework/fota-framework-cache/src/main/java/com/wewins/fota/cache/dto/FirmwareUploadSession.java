package com.wewins.fota.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 固件上传会话缓存对象。
 * <p>
 * 用于两阶段上传流程：
 * <ol>
 *   <li>客户端上传文件到临时目录，创建会话（状态=UPLOADED）</li>
 *   <li>客户端提交固件版本，携带 uploadSessionId</li>
 *   <li>服务端消费会话，转存到对象存储，更新状态为 READY</li>
 * </ol>
 * </p>
 * <p>
 * 存储位置：Redis（key: fota:fw:upload:sess:{sessionId}, TTL: 2h）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmwareUploadSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID（UUID 无横线格式）
     */
    private String sessionId;

    /**
     * 会话状态
     * <ul>
     *   <li>NONE - 初始状态</li>
     *   <li>UPLOADED - 已上传临时文件</li>
     *   <li>READY - 已转存到对象存储</li>
     *   <li>FAILED - 上传或转存失败</li>
     * </ul>
     */
    private UploadStatus status;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 版本号（可选，提交时填写）
     */
    private String version;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MD5 哈希值（32 位小写十六进制）
     */
    private String md5;

    /**
     * SHA-256 哈希值（64 位小写十六进制）
     */
    private String sha256;

    /**
     * MIME 类型
     */
    private String mime;

    /**
     * 临时文件路径（本地 staging 目录）
     */
    private String tempPath;

    /**
     * 对象存储 Key（转存成功后填写）
     */
    private String objectKey;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 最后一次错误信息
     */
    private String lastError;

    /**
     * 上传状态枚举。
     */
    public enum UploadStatus {
        /**
         * 初始状态
         */
        NONE,

        /**
         * 已上传临时文件（本地 staging）
         */
        UPLOADED,

        /**
         * 已转存到对象存储，可下载
         */
        READY,

        /**
         * 上传或转存失败
         */
        FAILED
    }
}
