package com.wewins.fota.adapter.api.admin;

import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.wewins.fota.adapter.api.admin.dto.RealtimeMetricsDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class MonitorApiController {

    private final SystemLoadIndicator loadIndicator;
    private static final String RESOURCE_NAME = "upgrade:check";

    @GetMapping("/realtime")
    public ApiResponse<RealtimeMetricsDTO> getRealtimeMetrics() {
        LoadSnapshot snapshot = loadIndicator.getSnapshot();

        ClusterNode node = ClusterBuilderSlot.getClusterNode(RESOURCE_NAME);
        int activeRequests = getActiveRequests(node);
        double blockRate = getBlockRate(node);
        String circuitState = getCircuitState();

        RealtimeMetricsDTO dto = RealtimeMetricsDTO.builder()
                .loadScore(snapshot.totalScore())
                .loadLevel(snapshot.level().name())
                .cpuUsage(snapshot.cpuUsage())
                .memoryUsage(snapshot.memoryUsage())
                .currentQps(snapshot.qps())
                .p99Latency(snapshot.p99Latency())
                .activeRequests(activeRequests)
                .blockRate(blockRate)
                .circuitState(circuitState)
                .timestamp(Instant.now())
                .build();

        return ApiResponse.success(dto);
    }

    private int getActiveRequests(ClusterNode node) {
        if (node == null) {
            return 0;
        }
        return (int) node.passQps();
    }

    private double getBlockRate(ClusterNode node) {
        if (node == null) {
            return 0.0;
        }
        double totalQps = node.totalQps();
        if (totalQps <= 0) {
            return 0.0;
        }
        double blockQps = node.blockQps();
        return blockQps / totalQps;
    }

    private String getCircuitState() {
        List<DegradeRule> rules = DegradeRuleManager.getRules();
        boolean hasRule = rules.stream().anyMatch(r -> RESOURCE_NAME.equals(r.getResource()));
        if (!hasRule) {
            return "UNKNOWN";
        }
        return "CLOSED";
    }
}
