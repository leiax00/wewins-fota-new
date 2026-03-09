package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.sentinel.SentinelRuleAppService;
import com.wewins.fota.application.sentinel.dto.SentinelConfigDTO;
import com.wewins.fota.application.sentinel.dto.SentinelDegradeRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelFlowRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sentinel 流控降级规则管理控制器
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/sentinel")
@RequiredArgsConstructor
public class SentinelAdminController {

    private final SentinelRuleAppService sentinelRuleAppService;

    /**
     * 获取 Sentinel 配置信息
     */
    @GetMapping("/config")
    @PreAuthorize("@rbac.has('fota:sentinel:read')")
    public ApiResponse<SentinelConfigDTO> getConfig() {
        return ApiResponse.success(sentinelRuleAppService.getConfig());
    }

    /**
     * 获取当前生效的流控和降级规则
     */
    @GetMapping("/rules")
    @PreAuthorize("@rbac.has('fota:sentinel:read')")
    public ApiResponse<SentinelRulesDTO> getRules() {
        return ApiResponse.success(sentinelRuleAppService.getRules());
    }

    /**
     * 更新流控规则
     */
    @PutMapping("/rules/flow")
    @PreAuthorize("@rbac.has('fota:sentinel:write')")
    public ApiResponse<Void> updateFlowRules(
            @Valid @RequestBody List<SentinelFlowRuleDTO> rules,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        sentinelRuleAppService.updateFlowRules(rules);
        log.info("Flow rules updated by {}", operator);
        return ApiResponse.success();
    }

    /**
     * 更新降级规则
     */
    @PutMapping("/rules/degrade")
    @PreAuthorize("@rbac.has('fota:sentinel:write')")
    public ApiResponse<Void> updateDegradeRules(
            @Valid @RequestBody List<SentinelDegradeRuleDTO> rules,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        sentinelRuleAppService.updateDegradeRules(rules);
        log.info("Degrade rules updated by {}", operator);
        return ApiResponse.success();
    }

    /**
     * 手动刷新规则（从 Redis 重新加载）
     */
    @PostMapping("/rules/refresh")
    @PreAuthorize("@rbac.has('fota:sentinel:write')")
    public ApiResponse<Void> refreshRules() {
        sentinelRuleAppService.refreshRules();
        log.info("Sentinel rules refreshed manually");
        return ApiResponse.success();
    }
}
