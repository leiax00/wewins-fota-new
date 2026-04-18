package com.wewins.fota.application.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySummaryDTO {

    private Long affectedTotal;

    private Long upgradedTotal;

    private Long pendingUpgradeTotal;

    private LocalDateTime dataCalculatedAt;
}
