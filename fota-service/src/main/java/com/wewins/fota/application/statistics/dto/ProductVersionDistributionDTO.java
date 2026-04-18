package com.wewins.fota.application.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVersionDistributionDTO {

    private List<VersionDistributionItem> versionDistributions;

    private Long neverVisitedCount;

    private Long totalDevices;

    private LocalDateTime dataCalculatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionDistributionItem {

        private Long versionId;

        private String version;

        private String internalVersion;

        private Long deviceCount;

        private Double percentage;
    }
}
