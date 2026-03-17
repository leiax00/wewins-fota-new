package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.RealtimeMetricsDTO;
import com.wewins.fota.adapter.api.admin.dto.HotProductDTO;
import com.wewins.fota.adapter.api.admin.dto.MonitorTrendsDTO;
import com.wewins.fota.adapter.api.admin.dto.OperationLogDetailDTO;
import com.wewins.fota.adapter.api.admin.dto.OperationLogRespDTO;
import com.wewins.fota.adapter.api.admin.dto.GlobalMonitorMetricsDTO;
import com.wewins.fota.adapter.api.admin.dto.RegionDetailDTO;
import com.wewins.fota.adapter.api.admin.dto.InstanceDetailDTO;
import com.wewins.fota.application.audit.OperationLogAppService;
import com.wewins.fota.application.audit.dto.OperationLogPageReqDTO;
import com.wewins.fota.application.monitor.MonitorOverviewService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.infra.logging.SseLogBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class MonitorApiController {

    private final MonitorOverviewService monitorOverviewService;
    private final SseLogBroadcaster sseLogBroadcaster;
    private final OperationLogAppService operationLogAppService;

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

    /**
     * 实时日志流 SSE 端点
     * <p>
     * 返回 Server-Sent Events 流，用于实时推送应用日志
     */
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@rbac.has('monitor:log:read')")
    public SseEmitter streamLogs() {
        log.debug("创建新的日志流 SSE 连接");
        return sseLogBroadcaster.connect();
    }

    @GetMapping("/operation-logs")
    @PreAuthorize("@rbac.has('monitor:operation-log:read')")
    public ApiResponse<PageResponse<OperationLogRespDTO>> pageOperationLogs(@ModelAttribute OperationLogPageReqDTO reqDTO) {
        var pageResult = operationLogAppService.page(reqDTO);
        List<OperationLogRespDTO> records = pageResult.getRecords().stream()
                .map(operationLog -> OperationLogRespDTO.builder()
                        .id(operationLog.getId())
                        .moduleCode(operationLog.getModuleCode())
                        .resourceCode(operationLog.getResourceCode())
                        .actionCode(operationLog.getActionCode())
                        .operationType(operationLog.getOperationType())
                        .targetId(operationLog.getTargetId())
                        .targetName(operationLog.getTargetName())
                        .operatorId(operationLog.getOperatorId())
                        .operatorUsername(operationLog.getOperatorUsername())
                        .operatorDisplayName(operationLog.getOperatorDisplayName())
                        .clientIp(operationLog.getClientIp())
                        .occurredAt(operationLog.getOccurredAt())
                        .build())
                .toList();

        return ApiResponse.success(PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        ));
    }

    @GetMapping("/operation-logs/{id}")
    @PreAuthorize("@rbac.has('monitor:operation-log:read')")
    public ApiResponse<OperationLogDetailDTO> getOperationLog(@PathVariable Long id) {
        try {
            var operationLog = operationLogAppService.getById(id);
            return ApiResponse.success(OperationLogDetailDTO.builder()
                    .id(operationLog.getId())
                    .moduleCode(operationLog.getModuleCode())
                    .resourceCode(operationLog.getResourceCode())
                    .actionCode(operationLog.getActionCode())
                    .operationType(operationLog.getOperationType())
                    .targetId(operationLog.getTargetId())
                    .targetName(operationLog.getTargetName())
                    .operatorId(operationLog.getOperatorId())
                    .operatorUsername(operationLog.getOperatorUsername())
                    .operatorDisplayName(operationLog.getOperatorDisplayName())
                    .requestMethod(operationLog.getRequestMethod())
                    .requestPath(operationLog.getRequestPath())
                    .requestQuery(operationLog.getRequestQuery())
                    .requestBody(operationLog.getRequestBody())
                    .clientIp(operationLog.getClientIp())
                    .userAgent(operationLog.getUserAgent())
                    .occurredAt(operationLog.getOccurredAt())
                    .build());
        } catch (BizException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    // ==================== 全局监控 API 端点 ====================

    /**
     * 获取全局监控指标
     * <p>
     * 返回全系统聚合指标，包括全局摘要、各区域指标、主机指标、实例指标和热点产品指标
     */
    @GetMapping("/global")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<GlobalMonitorMetricsDTO> getGlobalMetrics() {
        return ApiResponse.success(monitorOverviewService.getGlobalMetrics());
    }

    /**
     * 获取区域详情
     * <p>
     * 返回指定区域的完整监控信息，包括摘要指标、主机列表、实例列表和热点产品
     *
     * @param region 区域代码
     */
    @GetMapping("/regions/{region}")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<RegionDetailDTO> getRegionDetail(@PathVariable String region) {
        return ApiResponse.success(monitorOverviewService.getRegionDetail(region));
    }

    /**
     * 获取实例详情
     * <p>
     * 返回指定实例的完整监控信息，包括摘要指标、主机信息和热点产品
     *
     * @param instance 实例标识
     */
    @GetMapping("/instances/{instance}")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<InstanceDetailDTO> getInstanceDetail(@PathVariable String instance) {
        return ApiResponse.success(monitorOverviewService.getInstanceDetail(instance));
    }
}
