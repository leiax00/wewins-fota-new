package com.wewins.fota.infra.sentinel;

import com.wewins.fota.adapter.api.device.dto.UpgradeCheckRespDTO;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.load.DynamicIntervalService;
import com.wewins.fota.application.reporting.BlockedCheckLogRecorder;
import com.wewins.fota.application.upgrade.dto.CheckLogContext;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.common.util.IdGenerator;
import com.wewins.fota.domain.base.entity.DomainEntity;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.metrics.FotaMetrics;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 升级检查 API 的 Sentinel BlockHandler
 */
@Slf4j
@Component
public class UpgradeCheckBlockHandler {

    private final DynamicIntervalService dynamicIntervalService;
    private final FotaMetrics fotaMetrics;
    private final BlockedCheckLogRecorder blockedCheckLogRecorder;
    private final ProductRepository productRepository;

    public UpgradeCheckBlockHandler(
            DynamicIntervalService dynamicIntervalService,
            FotaMetrics fotaMetrics,
            BlockedCheckLogRecorder blockedCheckLogRecorder,
            ProductRepository productRepository
    ) {
        this.dynamicIntervalService = dynamicIntervalService;
        this.fotaMetrics = fotaMetrics;
        this.blockedCheckLogRecorder = blockedCheckLogRecorder;
        this.productRepository = productRepository;
    }

    public UpgradeCheckRespDTO handleBlock(
            UpgradeCheckReqDTO request,
            CheckLogContext logContext,
            BlockException ex) {
        String reason = getBlockedReason(ex);
        Long productId = productRepository.findByModel(request.getProduct()).map(DomainEntity::getId).orElse(null);
        int checkInterval = dynamicIntervalService.calculateProtectedCheckInterval(productId, reason);
        fotaMetrics.recordRateLimited("upgrade:check", reason);

        CheckResult result = CheckResult.rateLimited(IdGenerator.simpleUUID(), "请求被系统保护", checkInterval);
        result.setErrorCode(reason);
        result.setDownloadDelay(checkInterval);
        blockedCheckLogRecorder.recordIfKnownDevice(request, result, logContext);

        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.RATE_LIMITED.getCode())
                .requestId(result.getRequestId())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(checkInterval)
                        .downloadDelay(checkInterval)
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
