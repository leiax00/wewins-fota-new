package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.statistics.DeviceTimelineService;
import com.wewins.fota.application.statistics.FirmwareStatisticsService;
import com.wewins.fota.application.statistics.PolicyStatisticsService;
import com.wewins.fota.application.statistics.ProductStatisticsService;
import com.wewins.fota.application.statistics.StatisticsRefreshService;
import com.wewins.fota.application.statistics.dto.DeviceTimelineDTO;
import com.wewins.fota.application.statistics.dto.FirmwareDeviceListDTO;
import com.wewins.fota.application.statistics.dto.PolicySummaryDTO;
import com.wewins.fota.application.statistics.dto.ProductVersionDistributionDTO;
import com.wewins.fota.application.statistics.dto.ProductVersionTrendDTO;
import com.wewins.fota.application.statistics.dto.StatisticsResponseDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计管理控制器。
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final ProductStatisticsService productStatisticsService;
    private final DeviceTimelineService deviceTimelineService;
    private final PolicyStatisticsService policyStatisticsService;
    private final FirmwareStatisticsService firmwareStatisticsService;
    private final StatisticsRefreshService statisticsRefreshService;

    @GetMapping("/products/{productId}/version-distribution")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<ProductVersionDistributionDTO>> getProductVersionDistribution(
            @PathVariable Long productId) {
        if (log.isDebugEnabled()) {
            log.debug("查询产品版本分布: productId={}", productId);
        }
        return ApiResponse.success(productStatisticsService.getVersionDistribution(productId));
    }

    @GetMapping("/products/{productId}/trend")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<ProductVersionTrendDTO>> getProductVersionTrend(
            @PathVariable Long productId,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days,
            @RequestParam(value = "granularity", required = false) String granularity) {
        if (log.isDebugEnabled()) {
            log.debug("查询产品版本趋势: productId={}, days={}, granularity={}", productId, days, granularity);
        }
        return ApiResponse.success(productStatisticsService.getVersionTrend(productId, days, granularity));
    }

    @GetMapping("/products/{productId}/devices")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<FirmwareDeviceListDTO>> getProductDevices(
            @PathVariable Long productId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (log.isDebugEnabled()) {
            log.debug("查询产品设备列表: productId={}, cursor={}, size={}, keyword={}", productId, cursor, size, keyword);
        }
        return ApiResponse.success(productStatisticsService.getProductDevices(productId, cursor, size, keyword));
    }

    @GetMapping("/devices/{imei}/timeline")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<DeviceTimelineDTO>> getDeviceTimeline(
            @PathVariable String imei,
            @RequestParam(value = "days", required = false, defaultValue = "30") int days,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size) {
        if (log.isDebugEnabled()) {
            log.debug("查询设备升级轨迹: imei={}, days={}, cursor={}, size={}", imei, days, cursor, size);
        }
        return ApiResponse.success(deviceTimelineService.getTimeline(imei, days, cursor, size));
    }

    @GetMapping("/policies/{policyId}/summary")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<PolicySummaryDTO>> getPolicySummary(@PathVariable Long policyId) {
        if (log.isDebugEnabled()) {
            log.debug("查询策略统计汇总: policyId={}", policyId);
        }
        return ApiResponse.success(policyStatisticsService.getSummary(policyId));
    }

    @GetMapping("/firmware/{versionId}/device-count")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<Long>> getFirmwareDeviceCount(@PathVariable Long versionId) {
        if (log.isDebugEnabled()) {
            log.debug("查询固件设备数: versionId={}", versionId);
        }
        return ApiResponse.success(firmwareStatisticsService.getDeviceCount(versionId));
    }

    @GetMapping("/firmware/{versionId}/devices")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<StatisticsResponseDTO<FirmwareDeviceListDTO>> getFirmwareDevices(
            @PathVariable Long versionId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (log.isDebugEnabled()) {
            log.debug("查询固件设备列表: versionId={}, cursor={}, size={}, keyword={}", versionId, cursor, size, keyword);
        }
        return ApiResponse.success(firmwareStatisticsService.getDevices(versionId, cursor, size, keyword));
    }

    @PostMapping("/refresh")
    @PreAuthorize("@rbac.has('fota:statistics:read')")
    public ApiResponse<Void> refreshStatistics(
            @RequestParam("scope") String scope,
            @RequestParam(value = "scopeId", required = false) Long scopeId) {
        log.info("手动触发统计刷新: scope={}, scopeId={}", scope, scopeId);
        return statisticsRefreshService.refresh(scope, scopeId);
    }
}
