package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.DeviceImportBatchAssembler;
import com.wewins.fota.application.device.DeviceImportBatchAppService;
import com.wewins.fota.application.device.dto.DeviceImportBatchPageReqDTO;
import com.wewins.fota.application.device.dto.DeviceImportBatchRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备导入批次管理控制器
 * <p>
 * 提供设备导入批次的查询接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/device-import-batches")
@RequiredArgsConstructor
public class DeviceImportBatchController {

    private final DeviceImportBatchAppService deviceImportBatchAppService;
    private final DeviceImportBatchAssembler deviceImportBatchAssembler;

    /**
     * 分页查询批次列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<PageResponse<DeviceImportBatchRespDTO>> pageBatches(@ModelAttribute DeviceImportBatchPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DeviceImportBatchPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询设备导入批次: batchName={}, status={}, page={}, size={}",
                    reqDTO.getBatchName(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        try {
            Page<DeviceImportBatch> pageResult = deviceImportBatchAppService.pageBatches(reqDTO);
            List<DeviceImportBatchRespDTO> records = pageResult.getRecords().stream()
                    .map(deviceImportBatchAssembler::toDeviceImportBatchResp)
                    .toList();

            PageResponse<DeviceImportBatchRespDTO> response = PageResponse.of(
                    records,
                    (int) pageResult.getCurrent(),
                    (int) pageResult.getSize(),
                    pageResult.getTotal()
            );
            return ApiResponse.success(response);
        } catch (BizException e) {
            log.warn("分页查询设备导入批次失败: errorCode={}, message={}", e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("分页查询设备导入批次参数错误: message={}", e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 获取批次详情
     *
     * @param id 批次ID
     * @return 批次详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<DeviceImportBatchRespDTO> getBatch(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取设备导入批次详情: batchId={}", id);
        }

        try {
            DeviceImportBatch batch = deviceImportBatchAppService.getById(id);
            DeviceImportBatchRespDTO respDTO = deviceImportBatchAssembler.toDeviceImportBatchResp(batch);
            return ApiResponse.success(respDTO);
        } catch (BizException e) {
            log.warn("获取设备导入批次详情失败: batchId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取设备导入批次详情参数错误: batchId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
