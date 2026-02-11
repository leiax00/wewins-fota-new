package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.policy.PolicyApplicationService;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<UpgradePolicy>> listPolicies(@RequestParam Long productId) {
        List<UpgradePolicy> policies = policyApplicationService.getPoliciesByProduct(productId);
        return ResponseEntity.ok(policies);
    }

    /**
     * 获取策略详情
     *
     * @param id 策略 ID
     * @return 策略详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<UpgradePolicy> getPolicy(@PathVariable Long id) {
        UpgradePolicy policy = policyApplicationService.getPolicy(id);
        if (policy == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(policy);
    }

    /**
     * 创建升级策略
     *
     * @param policy 策略实体
     * @return 创建的策略
     */
    @PostMapping
    public ResponseEntity<UpgradePolicy> createPolicy(@RequestBody UpgradePolicy policy) {
        Long policyId = policyApplicationService.createPolicy(policy);
        UpgradePolicy created = policyApplicationService.getPolicy(policyId);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新升级策略
     *
     * @param id 策略 ID
     * @param policy 策略实体
     * @return 更新的策略
     */
    @PutMapping("/{id}")
    public ResponseEntity<UpgradePolicy> updatePolicy(@PathVariable Long id, @RequestBody UpgradePolicy policy) {
        policy.setId(id);
        int rows = policyApplicationService.updatePolicy(policy);
        return ResponseEntity.ok(policy);
    }

    /**
     * 删除升级策略
     *
     * @param id 策略 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        policyApplicationService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 激活升级策略
     *
     * @param id 策略 ID
     * @return 200 OK
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activatePolicy(@PathVariable Long id) {
        policyApplicationService.activatePolicy(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 暂停升级策略
     *
     * @param id 策略 ID
     * @return 200 OK
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivatePolicy(@PathVariable Long id) {
        policyApplicationService.deactivatePolicy(id);
        return ResponseEntity.ok().build();
    }
}
