package com.wewins.fota.domain.load.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 控制参数实体
 * <p>
 * 用于存储全局或产品级的控制参数，支持动态调整设备行为
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlParameter {

    private Long id;

    private Long productId;

    private Double checkIntervalMultiplier;

    private Double downloadDelayMultiplier;

    private Boolean forceMaintenance;

    private String maintenanceMessage;

    private Instant updatedAt;

    private String updatedBy;

    public boolean isGlobal() {
        return productId == null;
    }

    public boolean isProductLevel() {
        return productId != null;
    }

    public static ControlParameter createGlobalDefault() {
        return ControlParameter.builder()
                .checkIntervalMultiplier(1.0)
                .downloadDelayMultiplier(1.0)
                .forceMaintenance(false)
                .maintenanceMessage("")
                .updatedAt(Instant.now())
                .build();
    }

    public static ControlParameter createProductDefault(Long productId) {
        return ControlParameter.builder()
                .productId(productId)
                .checkIntervalMultiplier(1.0)
                .downloadDelayMultiplier(1.0)
                .forceMaintenance(false)
                .maintenanceMessage("")
                .updatedAt(Instant.now())
                .build();
    }
}
