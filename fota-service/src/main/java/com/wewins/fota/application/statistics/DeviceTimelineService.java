package com.wewins.fota.application.statistics;

import com.wewins.fota.application.statistics.dto.DeviceTimelineDTO;
import com.wewins.fota.application.statistics.dto.StatisticsResponseDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.infra.persistence.clickhouse.reporting.mapper.TimelineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTimelineService {

    private static final String SCOPE_DEVICE = "device";
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final TimelineMapper timelineMapper;

    public StatisticsResponseDTO<DeviceTimelineDTO> getTimeline(String imei, int days, Long cursor, int size) {
        if (!StringUtils.hasText(imei)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "IMEI 不能为空");
        }

        int safeDays = normalizeDays(days);
        int safeSize = normalizeSize(size);

        if (log.isDebugEnabled()) {
            log.debug("查询设备升级轨迹: imei={}, days={}, cursor={}, size={}", imei, safeDays, cursor, safeSize);
        }

        List<DeviceTimelineDTO.TimelineItem> timelineItems = timelineMapper.getDeviceTimeline(imei, safeDays, cursor, safeSize).stream()
                .map(this::toTimelineItem)
                .toList();

        Long nextCursor = timelineItems.size() < safeSize ? null : timelineItems.get(timelineItems.size() - 1).getCursor();
        LocalDateTime dataCalculatedAt = LocalDateTime.now();

        DeviceTimelineDTO data = DeviceTimelineDTO.builder()
                .timelineItems(timelineItems)
                .nextCursor(nextCursor)
                .dataCalculatedAt(dataCalculatedAt)
                .build();

        return StatisticsResponseDTO.<DeviceTimelineDTO>builder()
                .data(data)
                .dataCalculatedAt(dataCalculatedAt)
                .scope(SCOPE_DEVICE)
                .scopeId(null)
                .build();
    }

    private DeviceTimelineDTO.TimelineItem toTimelineItem(Map<String, Object> row) {
        return DeviceTimelineDTO.TimelineItem.builder()
                .cursor(asLong(row.get("cursor")))
                .eventTime(asLocalDateTime(row.get("eventTime")))
                .eventType(asString(row.get("eventType")))
                .requestId(asString(row.get("requestId")))
                .policyId(asLong(row.get("policyId")))
                .checkResult(asString(row.get("checkResult")))
                .targetVersionId(asLong(row.get("targetVersionId")))
                .targetVersion(asString(row.get("targetVersion")))
                .targetInternalVersion(asString(row.get("targetInternalVersion")))
                .details(asString(row.get("details")))
                .build();
    }

    private int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }
}
