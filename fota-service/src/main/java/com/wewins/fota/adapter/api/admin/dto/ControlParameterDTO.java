package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlParameterDTO {

    private Long productId;

    private Double checkIntervalMultiplier;

    private Double downloadDelayMultiplier;

    private Boolean forceMaintenance;

    private String maintenanceMessage;

    private Instant updatedAt;

    private String updatedBy;
}
