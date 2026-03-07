package com.wewins.fota.domain.device.model.aggregate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备信息更新消息
 * <p>
 * 用于异步更新设备信息（首次上线时间、当前版本、最后访问时间等）
 * 通过 RabbitMQ 队列发送，由消费者批量处理
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfoUpdateMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一标识（用于幂等性）
     */
    private String messageId;

    /**
     * 消息时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 关联的请求 ID（来自 check API）
     */
    private String correlationId;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 设备 IMEI
     */
    private String imei;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 更新原因
     */
    private UpdateReason updateReason;

    /**
     * 是否第一次上线
     */
    private Boolean isFirstOnline;

    /**
     * 新版本（请求中的版本）
     */
    private String newVersion;

    /**
     * 新版本 ID
     */
    private Long newVersionId;

    /**
     * 版本部分名称（main, bootloader 等）
     */
    private String partName;

    /**
     * 旧版本（缓存/数据库中的版本，可能为 null）
     */
    private String oldVersion;

    /**
     * 旧版本 ID（可能为 null）
     */
    private Long oldVersionId;

    /**
     * 访问时间（check API 调用时间）
     */
    private LocalDateTime accessTime;

    /**
     * 更新原因枚举
     */
    public enum UpdateReason {
        /**
         * 第一次上线
         */
        FIRST_ONLINE,

        /**
         * 版本变化
         */
        VERSION_CHANGED,

        /**
         * 仅更新访问时间
         */
        ACCESS_TIME_UPDATE
    }
}
