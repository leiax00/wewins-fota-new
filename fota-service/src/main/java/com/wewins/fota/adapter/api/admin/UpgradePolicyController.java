package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.policy.PolicyApplicationService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 升级策略管理控制器
 * <p>
 * 提供升级策略 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/admin/policy")
@ConditionalOnAppMode("main")
@RequiredArgsConstructor
public class UpgradePolicyController {

    private final PolicyApplicationService policyApplicationService;

    /**
     * 获取策略列表
     *
     * @param productId 产品 ID
     * @return 策略列表
     */
    @GetMapping
    public ApiResponse<List<UpgradePolicy>> listPolicies(@RequestParam Long productId) {
        List<UpgradePolicy> policies = policyApplicationService.getPoliciesByProduct(productId);
        return ApiResponse.success(policies);
    }

    /**
     * 获取策略详情
     *
     * @param id 策略 ID
     * @return 策略详情
     */
    @GetMapping("/{id}")
    public ApiResponse<UpgradePolicy> getPolicy(@PathVariable Long id) {
        UpgradePolicy policy = policyApplicationService.getPolicy(id);
        if (policy == null) {
            return ApiResponse.error(404, "策略不存在");
        }
        return ApiResponse.success(policy);
    }

    /**
     * 创建升级策略
     *
     * @param policy 策略实体
     * @return 创建的策略
     */
    @PostMapping
    public ApiResponse<UpgradePolicy> createPolicy(@RequestBody UpgradePolicy policy) {
        Long policyId = policyApplicationService.createPolicy(policy);
        UpgradePolicy created = policyApplicationService.getPolicy(policyId);
        return ApiResponse.success(created);
    }

    /**
     * 更新升级策略
     *
     * @param id 策略 ID
     * @param policy 策略实体
     * @return 更新的策略
     */
    @PutMapping("/{id}")
    public ApiResponse<UpgradePolicy> updatePolicy(@PathVariable Long id, @RequestBody UpgradePolicy policy) {
        policy.setId(id);
        policyApplicationService.updatePolicy(policy);
        return ApiResponse.success(policy);
    }

    /**
     * 删除升级策略
     *
     * @param id 策略 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePolicy(@PathVariable Long id) {
        policyApplicationService.deletePolicy(id);
        return ApiResponse.success();
    }

    /**
     * 激活升级策略
     *
     * @param id 策略 ID
     * @return 激活结果
     */
    @PostMapping("/{id}/activate")
    public ApiResponse<Void> activatePolicy(@PathVariable Long id) {
        policyApplicationService.activatePolicy(id);
        return ApiResponse.success();
    }

    /**
     * 暂停升级策略
     *
     * @param id 策略 ID
     * @return 暂停结果
     */
    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivatePolicy(@PathVariable Long id) {
        policyApplicationService.deactivatePolicy(id);
        return ApiResponse.success();
    }
}
