package com.wewins.fota.application.statistics;

import com.wewins.fota.application.statistics.dto.FirmwareDeviceListDTO;
import com.wewins.fota.application.statistics.dto.ProductVersionDistributionDTO;
import com.wewins.fota.application.statistics.dto.ProductVersionTrendDTO;
import com.wewins.fota.application.statistics.dto.StatisticsResponseDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.infra.persistence.mybatis.mapper.statistics.StatVersionDeviceCountMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.statistics.StatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStatisticsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:00");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StatisticsMapper statisticsMapper;
    private final StatVersionDeviceCountMapper statVersionDeviceCountMapper;

    public StatisticsResponseDTO<ProductVersionDistributionDTO> getVersionDistribution(Long productId) {
        if (productId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("查询产品版本分布: productId={}", productId);
        }

        List<Map<String, Object>> rawRows = statisticsMapper.getProductVersionDistribution(productId);
        long totalDevices = rawRows.stream()
                .mapToLong(row -> {
                    Object v = row.get("deviceCount");
                    return v instanceof Number n ? n.longValue() : 0L;
                })
                .sum();

        List<ProductVersionDistributionDTO.VersionDistributionItem> versionDistributions = rawRows.stream()
                .map(row -> toVersionDistributionItem(row, totalDevices))
                .toList();

        LocalDateTime dataCalculatedAt = LocalDateTime.now();

        ProductVersionDistributionDTO data = ProductVersionDistributionDTO.builder()
                .versionDistributions(versionDistributions)
                .neverVisitedCount(0L)
                .totalDevices(totalDevices)
                .dataCalculatedAt(dataCalculatedAt)
                .build();

        return StatisticsResponseDTO.<ProductVersionDistributionDTO>builder()
                .data(data)
                .dataCalculatedAt(dataCalculatedAt)
                .scope("product")
                .scopeId(productId)
                .build();
    }

    public StatisticsResponseDTO<ProductVersionTrendDTO> getVersionTrend(Long productId, int days, String granularity) {
        if (productId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        int safeDays = Math.max(1, Math.min(90, days));
        int hours = safeDays * 24;
        String effectiveGranularity = resolveGranularity(safeDays, granularity);

        if (log.isDebugEnabled()) {
            log.debug("查询产品版本趋势: productId={}, days={}, granularity={}", productId, safeDays, effectiveGranularity);
        }

        List<Map<String, Object>> rawRows = statVersionDeviceCountMapper.getProductVersionTrend(productId, hours);
        ProductVersionTrendDTO data = pivotTrendData(rawRows, effectiveGranularity);

        LocalDateTime dataCalculatedAt = LocalDateTime.now();

        return StatisticsResponseDTO.<ProductVersionTrendDTO>builder()
                .data(data)
                .dataCalculatedAt(dataCalculatedAt)
                .scope("product")
                .scopeId(productId)
                .build();
    }

    public StatisticsResponseDTO<FirmwareDeviceListDTO> getProductDevices(Long productId, Long cursor, int size, String keyword) {
        if (productId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        int safeSize = normalizeSize(size);
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        if (log.isDebugEnabled()) {
            log.debug("查询产品设备列表: productId={}, cursor={}, size={}, keyword={}", productId, cursor, safeSize, safeKeyword);
        }

        List<FirmwareDeviceListDTO.DeviceItem> devices = statisticsMapper.getProductDeviceList(productId, cursor, safeSize, safeKeyword)
                .stream()
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
                .scope("product")
                .scopeId(productId)
                .build();
    }

    private ProductVersionDistributionDTO.VersionDistributionItem toVersionDistributionItem(
            Map<String, Object> row, long totalDevices) {
        long deviceCount = asLongDefault(row.get("deviceCount"), 0L);
        double percentage = totalDevices > 0 ? (deviceCount * 100.0 / totalDevices) : 0.0;

        return ProductVersionDistributionDTO.VersionDistributionItem.builder()
                .versionId(asLong(row.get("versionId")))
                .version(asString(row.get("version")))
                .internalVersion(asString(row.get("internalVersion")))
                .deviceCount(deviceCount)
                .percentage(Math.round(percentage * 10.0) / 10.0)
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

    private ProductVersionTrendDTO pivotTrendData(List<Map<String, Object>> rawRows, String granularity) {
        if (rawRows.isEmpty()) {
            return ProductVersionTrendDTO.builder()
                    .timestamps(List.of())
                    .series(List.of())
                    .granularity(granularity)
                    .dataCalculatedAt(LocalDateTime.now())
                    .build();
        }

        Map<Long, String> versionNameMap = new LinkedHashMap<>();
        Map<Long, Map<String, Long>> versionTimestampCounts = new LinkedHashMap<>();

        for (Map<String, Object> row : rawRows) {
            Long versionId = asLong(row.get("versionId"));
            String version = asString(row.get("version"));
            LocalDateTime snapshotHour = asLocalDateTime(row.get("statTime"));
            Long deviceCount = asLongDefault(row.get("deviceCount"), 0L);

            if (versionId == null || snapshotHour == null) {
                continue;
            }

            String ts = formatTimestamp(snapshotHour, granularity);
            versionNameMap.putIfAbsent(versionId, version);
            versionTimestampCounts.computeIfAbsent(versionId, k -> new LinkedHashMap<>())
                    .merge(ts, deviceCount, Math::max);
        }

        Set<String> tsSet = new LinkedHashSet<>();
        for (Map<String, Long> tsCounts : versionTimestampCounts.values()) {
            tsSet.addAll(tsCounts.keySet());
        }
        List<String> timestamps = new ArrayList<>(tsSet);
        Collections.sort(timestamps);

        List<ProductVersionTrendDTO.VersionSeries> series = versionTimestampCounts.entrySet().stream()
                .map(entry -> {
                    Long versionId = entry.getKey();
                    Map<String, Long> tsCounts = entry.getValue();
                    List<Long> counts = timestamps.stream()
                            .map(ts -> tsCounts.getOrDefault(ts, 0L))
                            .collect(Collectors.toList());
                    return ProductVersionTrendDTO.VersionSeries.builder()
                            .versionId(versionId)
                            .version(versionNameMap.get(versionId))
                            .counts(counts)
                            .build();
                })
                .sorted((a, b) -> {
                    long sumA = a.getCounts().stream().mapToLong(Long::longValue).sum();
                    long sumB = b.getCounts().stream().mapToLong(Long::longValue).sum();
                    return Long.compare(sumB, sumA);
                })
                .collect(Collectors.toList());

        return ProductVersionTrendDTO.builder()
                .timestamps(timestamps)
                .series(series)
                .granularity(granularity)
                .dataCalculatedAt(LocalDateTime.now())
                .build();
    }

    private String resolveGranularity(int days, String granularity) {
        if (StringUtils.hasText(granularity)) {
            return granularity;
        }
        return days <= 3 ? "hour" : "day";
    }

    private String formatTimestamp(LocalDateTime dt, String granularity) {
        if ("hour".equals(granularity)) {
            return dt.format(HOUR_FORMATTER);
        }
        return dt.format(DAY_FORMATTER);
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private long asLongDefault(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }
}
