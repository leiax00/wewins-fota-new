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
public class ProductVersionTrendDTO {

    private List<String> timestamps;

    private List<VersionSeries> series;

    private String granularity;

    private LocalDateTime dataCalculatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionSeries {

        private Long versionId;

        private String version;

        private List<Long> counts;
    }
}
