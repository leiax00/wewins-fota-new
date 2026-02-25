package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.UpgradePolicyAssembler;
import com.wewins.fota.application.policy.UpgradePolicyAppService;
import com.wewins.fota.application.policy.dto.UpgradePolicyPageReqDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyReqDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class UpgradePolicyController {

    private final UpgradePolicyAppService upgradePolicyAppService;
    private final UpgradePolicyAssembler upgradePolicyAssembler;

    /**
     * 分页查询策略列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:policy:read')")
    public ApiResponse<PageResponse<UpgradePolicyRespDTO>> listPolicies(@ModelAttribute UpgradePolicyPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new UpgradePolicyPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询升级策略: productId={}, name={}, status={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getName(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<UpgradePolicy> pageResult = upgradePolicyAppService.pagePolicies(reqDTO);
        List<UpgradePolicyRespDTO> records = pageResult.getRecords().stream()
                .map(upgradePolicyAssembler::toUpgradePolicyResp)
                .toList();

        PageResponse<UpgradePolicyRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    /**
     * 获取策略详情
     *
     * @param id 策略 ID
     * @return 策略响应
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:policy:read')")
    public ApiResponse<UpgradePolicyRespDTO> getPolicy(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取升级策略详情: policyId={}", id);
        }

        try {
            UpgradePolicy policy = upgradePolicyAppService.getById(id);
            return ApiResponse.success(upgradePolicyAssembler.toUpgradePolicyResp(policy));
        } catch (BizException e) {
            log.warn("获取升级策略详情失败: policyId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取升级策略详情参数错误: policyId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 创建升级策略
     *
     * @param reqDTO 策略请求
     * @return 创建的策略
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:policy:create')")
    public ApiResponse<UpgradePolicyRespDTO> createPolicy(@RequestBody UpgradePolicyReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建升级策略: productId={}, firmwareVersionId={}, name={}, grayRate={}, priority={}, status={}",
                    reqDTO.getProductId(), reqDTO.getFirmwareVersionId(), reqDTO.getName(),
                    reqDTO.getGrayRate(), reqDTO.getPriority(), reqDTO.getStatus());
        }

        try {
            UpgradePolicy policy = upgradePolicyAssembler.toUpgradePolicyEntity(reqDTO);
            policy.setId(null);
            UpgradePolicy createdPolicy = upgradePolicyAppService.createPolicy(policy);
            log.info("升级策略创建成功: policyId={}, name={}", createdPolicy.getId(), createdPolicy.getName());
            return ApiResponse.success(upgradePolicyAssembler.toUpgradePolicyResp(createdPolicy));
        } catch (BizException e) {
            log.warn("创建升级策略失败: name={}, errorCode={}, message={}", reqDTO.getName(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建升级策略参数错误: name={}, message={}", reqDTO.getName(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 更新升级策略
     *
     * @param id 策略 ID
     * @param reqDTO 策略请求
     * @return 更新的策略
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:policy:update')")
    public ApiResponse<UpgradePolicyRespDTO> updatePolicy(@PathVariable Long id, @RequestBody UpgradePolicyReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新升级策略: policyId={}", id);
        }

        try {
            UpgradePolicy policy = upgradePolicyAssembler.toUpgradePolicyEntity(reqDTO);
            policy.setId(id);
            UpgradePolicy updatedPolicy = upgradePolicyAppService.updatePolicy(policy);
            log.info("升级策略更新成功: policyId={}", updatedPolicy.getId());
            return ApiResponse.success(upgradePolicyAssembler.toUpgradePolicyResp(updatedPolicy));
        } catch (BizException e) {
            log.warn("更新升级策略失败: policyId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新升级策略参数错误: policyId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 删除升级策略
     *
     * @param id 策略 ID
     * @return 删除响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:policy:delete')")
    public ApiResponse<Void> deletePolicy(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除升级策略: policyId={}", id);
        }

        try {
            upgradePolicyAppService.deletePolicy(id);
            log.info("升级策略删除成功: policyId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除升级策略失败: policyId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除升级策略参数错误: policyId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
