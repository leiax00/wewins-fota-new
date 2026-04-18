package com.wewins.fota.application.statistics;

import com.wewins.fota.application.statistics.dto.FirmwareDeviceListDTO;
import com.wewins.fota.application.statistics.dto.StatisticsResponseDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.infra.persistence.mybatis.mapper.statistics.StatisticsMapper;
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
public class FirmwareStatisticsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final StatisticsMapper statisticsMapper;

    public StatisticsResponseDTO<Long> getDeviceCount(Long versionId) {
        if (versionId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "固件版本 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("查询固件设备数: versionId={}", versionId);
        }

        Long count = statisticsMapper.getFirmwareDeviceCount(versionId);
        LocalDateTime dataCalculatedAt = LocalDateTime.now();

        return StatisticsResponseDTO.<Long>builder()
                .data(count == null ? 0L : count)
                .dataCalculatedAt(dataCalculatedAt)
                .scope("firmware")
                .scopeId(versionId)
                .build();
    }

    public StatisticsResponseDTO<FirmwareDeviceListDTO> getDevices(Long versionId, Long cursor, int size, String keyword) {
        if (versionId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "固件版本 ID 不能为空");
        }

        int safeSize = normalizeSize(size);
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        if (log.isDebugEnabled()) {
            log.debug("查询固件设备列表: versionId={}, cursor={}, size={}, keyword={}", versionId, cursor, safeSize, safeKeyword);
        }

        List<FirmwareDeviceListDTO.DeviceItem> devices = statisticsMapper.getFirmwareDevices(versionId, cursor, safeSize, safeKeyword).stream()
                .map(this::toDeviceItem)
                .toList();

        Long nextCursor = devices.size() < safeSize ? null : devices.get(devices.size() - 1).getId();
        LocalDateTime dataCalculatedAt = LocalDateTime.now();

        FirmwareDeviceListDTO data = FirmwareDeviceListDTO.builder()
                .devices(devices)
                .nextCursor(nextCursor)
                .dataCalculatedAt(dataCalculatedAt)
                .build();

        return StatisticsResponseDTO.<FirmwareDeviceListDTO>builder()
                .data(data)
                .dataCalculatedAt(dataCalculatedAt)
                .scope("firmware")
                .scopeId(versionId)
                .build();
    }

    private FirmwareDeviceListDTO.DeviceItem toDeviceItem(Map<String, Object> row) {
        return FirmwareDeviceListDTO.DeviceItem.builder()
                .id(asLong(row.get("id")))
                .imei(asString(row.get("imei")))
                .status(asString(row.get("status")))
                .productId(asLong(row.get("productId")))
                .firstSeenAt(asLocalDateTime(row.get("firstSeenAt")))
                .lastSeenAt(asLocalDateTime(row.get("lastSeenAt")))
                .version(asString(row.get("version")))
                .internalVersion(asString(row.get("internalVersion")))
                .build();
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }
}
