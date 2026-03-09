package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.RealtimeMetricsDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class MonitorApiController {

    private final SystemLoadIndicator loadIndicator;

    @GetMapping("/realtime")
    public ApiResponse<RealtimeMetricsDTO> getRealtimeMetrics() {
        LoadSnapshot snapshot = loadIndicator.getSnapshot();

        RealtimeMetricsDTO dto = RealtimeMetricsDTO.builder()
                .loadScore(snapshot.totalScore())
                .loadLevel(snapshot.level().name())
                .cpuUsage(snapshot.cpuUsage())
                .memoryUsage(snapshot.memoryUsage())
                .currentQps(snapshot.qps())
                .p99Latency(snapshot.p99Latency())
                .activeRequests(0)
                .blockRate(0.0)
                .circuitState("CLOSED")
                .timestamp(Instant.now())
                .build();

        return ApiResponse.success(dto);
    }
}
