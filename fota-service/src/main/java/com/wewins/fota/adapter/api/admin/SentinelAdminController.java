package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.sentinel.SentinelRuleAppService;
import com.wewins.fota.application.sentinel.dto.SentinelConfigDTO;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sentinel 流控降级规则管理控制器
 */
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
}
