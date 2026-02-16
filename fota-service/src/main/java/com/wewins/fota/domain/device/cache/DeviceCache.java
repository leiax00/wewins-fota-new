package com.wewins.fota.domain.device.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备缓存数据传输对象（DTO）
 * <p>
 * 用于 Redis 缓存，存储设备的基本信息
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCache implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 当前固件版本
     */
    private String firmwareVersion;

    /**
     * 当前策略 ID（可选）
     */
    private Long policyId;

    /**
     * 缓存时间
     */
    private LocalDateTime cachedAt;
}
