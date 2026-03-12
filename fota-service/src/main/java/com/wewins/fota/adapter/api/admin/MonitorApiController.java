package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.RealtimeMetricsDTO;
import com.wewins.fota.adapter.api.admin.dto.HotProductDTO;
import com.wewins.fota.adapter.api.admin.dto.MonitorTrendsDTO;
import com.wewins.fota.application.monitor.MonitorOverviewService;
import com.wewins.fota.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class MonitorApiController {

    private final MonitorOverviewService monitorOverviewService;

    @GetMapping("/realtime")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<RealtimeMetricsDTO> getRealtimeMetrics() {
        return ApiResponse.success(monitorOverviewService.getRealtimeMetrics());
    }

    @GetMapping("/products/hotspots")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<List<HotProductDTO>> getHotProducts() {
        return ApiResponse.success(monitorOverviewService.getHotProducts());
    }

    @GetMapping("/trends")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<MonitorTrendsDTO> getTrends(@RequestParam(value = "range", required = false) String range) {
        return ApiResponse.success(monitorOverviewService.getTrends(range));
    }
}
