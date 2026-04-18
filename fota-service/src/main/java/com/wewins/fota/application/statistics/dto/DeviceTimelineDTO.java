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
public class DeviceTimelineDTO {

    private List<TimelineItem> timelineItems;

    private Long nextCursor;

    private LocalDateTime dataCalculatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineItem {

        private Long cursor;

        private LocalDateTime eventTime;

        private String eventType;

        private String requestId;

        private Long policyId;

        private String checkResult;

        private Long targetVersionId;

        private String targetVersion;

        private String targetInternalVersion;

        private String details;
    }
}
