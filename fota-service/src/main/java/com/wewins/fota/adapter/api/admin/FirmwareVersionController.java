package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.policy.PolicyApplicationService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 固件版本管理控制器
 * <p>
 * 提供固件版本 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/admin/firmware")
@ConditionalOnAppMode("main")
@RequiredArgsConstructor
public class FirmwareVersionController {

    private final PolicyApplicationService policyApplicationService;

    /**
     * 获取固件版本列表
     *
     * @param productId 产品 ID
     * @return 固件版本列表
     */
    @GetMapping
    public ApiResponse<List<FirmwareVersion>> listVersions(@RequestParam Long productId) {
        // TODO: 实现固件版本列表查询
        return ApiResponse.success(List.of());
    }

    /**
     * 获取固件版本详情
     *
     * @param id 版本 ID
     * @return 版本详情
     */
    @GetMapping("/{id}")
    public ApiResponse<FirmwareVersion> getVersion(@PathVariable Long id) {
        // TODO: 实现固件版本详情查询
        return ApiResponse.success(new FirmwareVersion());
    }

    /**
     * 上传固件版本
     *
     * @param version 固件版本信息
     * @return 上传的版本
     */
    @PostMapping
    public ApiResponse<FirmwareVersion> uploadVersion(@RequestBody FirmwareVersion version) {
        // TODO: 实现固件上传和文件存储
        return ApiResponse.success(version);
    }

    /**
     * 删除固件版本
     *
     * @param id 版本 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteVersion(@PathVariable Long id) {
        // TODO: 实现固件版本删除
        return ApiResponse.success();
    }
}
