package com.wewins.fota.domain.device.model.aggregate;

import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
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
     * 是否第一次上线
     */
     private Boolean isFirstOnline;
    
    /**
     * 访问时间（check API 调用时间）
     */
    private LocalDateTime accessTime;

    /**
     * 当前版本 parts 的增量更新
     */
    private Map<String, DeviceVersionPart> currentVersionParts;

    /**
     * 初始版本 parts 的增量更新
     */
    private Map<String, DeviceVersionPart> initialVersionParts;

}
