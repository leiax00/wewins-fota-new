package com.wewins.fota.infra.sentinel;

import com.wewins.fota.adapter.api.device.dto.UpgradeCheckRespDTO;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.load.SmartBackoffHandler;
import com.wewins.fota.domain.load.model.vo.BackoffResult;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.infra.metrics.FotaMetrics;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 升级检查 API 的 Sentinel BlockHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpgradeCheckBlockHandler {

    private final SmartBackoffHandler backoffHandler;
    private final SystemLoadIndicator loadIndicator;
    private final FotaMetrics fotaMetrics;

    public UpgradeCheckRespDTO handleBlock(
            com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO request,
            BlockException ex) {

        LoadSnapshot load = loadIndicator.getSnapshot();
        String reason = getBlockedReason(ex);
        BackoffResult backoff = backoffHandler.calculateBackoff(reason, load);
        fotaMetrics.recordRateLimited("upgrade:check", reason);

        log.warn("Request blocked: imei={}, reason={}, backoff={}s",
                request.getImei(), reason, backoff.retryAfterSeconds());

        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.RATE_LIMITED.getCode())
                .requestId(java.util.UUID.randomUUID().toString())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(backoff.retryAfterSeconds())
                        .downloadDelay(backoff.retryAfterSeconds())
                        .build())
                .build();
    }

    private String getBlockedReason(BlockException ex) {
        if (ex instanceof FlowException) {
            return "FLOW_QPS";
        }
        if (ex instanceof DegradeException) {
            return "DEGRADE";
        }
        if (ex instanceof SystemBlockException) {
            return "SYSTEM";
        }
        return "UNKNOWN";
    }
}
