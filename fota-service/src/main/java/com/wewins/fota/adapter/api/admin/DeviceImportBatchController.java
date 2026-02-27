package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.DeviceAssembler;
import com.wewins.fota.adapter.assembler.DeviceImportBatchAssembler;
import com.wewins.fota.application.common.ReferenceNameResolver;
import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.application.device.DeviceImportBatchAppService;
import com.wewins.fota.application.device.dto.DeviceImportBatchPageReqDTO;
import com.wewins.fota.application.device.dto.DeviceImportBatchRespDTO;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.application.device.dto.DeviceRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final DeviceRepository deviceRepository;
    private final DeviceAssembler deviceAssembler;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final ReferenceNameResolver referenceNameResolver;

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

    /**
     * 获取批次下的设备列表
     *
     * @param id 批次ID
     * @param reqDTO 分页查询参数
     * @return 设备列表
     */
    @GetMapping("/{id}/devices")
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<PageResponse<DeviceRespDTO>> getBatchDevices(
            @PathVariable Long id,
            @ModelAttribute DevicePageReqDTO reqDTO) {

        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取批次下的设备列表: batchId={}, page={}, size={}", id, reqDTO.getPage(), reqDTO.getSize());
        }

        try {
            // 验证批次存在
            deviceImportBatchAppService.getById(id);

            if (reqDTO == null) {
                reqDTO = new DevicePageReqDTO();
            }

            Page<Device> pageResult = deviceRepository.pageByImportBatchId(
                    new Page<>(reqDTO.getPage(), reqDTO.getSize()), id);
            List<Device> devices = pageResult.getRecords();

            // 提取当前页中所有不同的产品 ID 和版本 ID
            Set<Long> productIds = devices.stream()
                    .map(Device::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<Long> versionIds = devices.stream()
                    .map(Device::getCurrentVersionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 使用 ReferenceNameResolver 批量查询名称
            Map<Long, String> productNameMap = referenceNameResolver.resolveProductNames(productIds);
            Map<Long, String> versionNameMap = referenceNameResolver.resolveFirmwareVersionNames(versionIds);

            // 转换为 DTO，填充产品名称和版本名称
            List<DeviceRespDTO> records = devices.stream()
                    .map(device -> deviceAssembler.toDeviceResp(
                            device,
                            productNameMap.get(device.getProductId()),
                            versionNameMap.get(device.getCurrentVersionId())
                    ))
                    .toList();

            PageResponse<DeviceRespDTO> response = PageResponse.of(
                    records,
                    (int) pageResult.getCurrent(),
                    (int) pageResult.getSize(),
                    pageResult.getTotal()
            );
            return ApiResponse.success(response);
        } catch (BizException e) {
            log.warn("获取批次下的设备列表失败: batchId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取批次下的设备列表参数错误: batchId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
