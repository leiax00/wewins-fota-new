package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.DeviceAssembler;
import com.wewins.fota.application.device.support.DeviceDisplayNameService;
import com.wewins.fota.application.device.support.DeviceNameContext;
import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.application.device.dto.BatchOperationReqDTO;
import com.wewins.fota.application.device.dto.BatchOperationResultDTO;
import com.wewins.fota.application.device.dto.DeviceImportEstimateRespDTO;
import com.wewins.fota.application.device.dto.DeviceImportExecuteReqDTO;
import com.wewins.fota.application.device.dto.DeviceImportRespDTO;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.application.device.dto.DeviceReqDTO;
import com.wewins.fota.application.device.dto.DeviceRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.model.entity.Device;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 设备管理控制器
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceAppService deviceAppService;
    private final DeviceAssembler deviceAssembler;
    private final DeviceDisplayNameService deviceDisplayNameService;

    /**
     * 分页查询设备列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<PageResponse<DeviceRespDTO>> listDevices(@ModelAttribute DevicePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DevicePageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询设备: productId={}, imei={}, status={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getImei(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<Device> pageResult = deviceAppService.pageDevices(reqDTO);
        List<Device> devices = pageResult.getRecords();

        // 提取当前页中所有不同的产品 ID、版本 ID 和批次 ID
        DeviceNameContext nameContext = deviceDisplayNameService.resolveForDevices(devices);

        // 转换为 DTO，填充产品名称、版本名称和批次名称
        List<DeviceRespDTO> records = devices.stream()
                .map(device -> deviceAssembler.toDeviceResp(device, nameContext))
                .toList();

        PageResponse<DeviceRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    /**
     * 获取设备详情
     *
     * @param id 设备 ID
     * @return 设备详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:device:detail')")
    public ApiResponse<DeviceRespDTO> getDevice(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取设备详情: deviceId={}", id);
        }

        try {
            Device device = deviceAppService.getById(id);
            DeviceRespDTO deviceResp = fillName(device);
            return ApiResponse.success(deviceResp);
        } catch (BizException e) {
            log.warn("获取设备详情失败: deviceId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取设备详情参数错误: deviceId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 预估设备导入 - 上传文件解析
     *
     * @param file 导入文件（Excel或TXT）
     * @return 预估结果（包含 sessionId 和统计信息）
     */
    @PostMapping("/import/estimate")
    @PreAuthorize("@rbac.has('fota:device:import')")
    public ApiResponse<DeviceImportEstimateRespDTO> estimateImport(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "导入文件不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("预估设备导入: filename={}", file.getOriginalFilename());
        }

        try {
            // 文件大小限制 10MB
            long maxSize = 10 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "文件大小超过10MB限制");
            }

            DeviceImportEstimateRespDTO result = deviceAppService.estimateImportDevices(file);
            log.info("设备导入预估成功: sessionId={}, totalCount={}, validCount={}, invalidCount={}",
                    result.getSessionId(), result.getTotalCount(), result.getValidCount(), result.getInvalidCount());
            return ApiResponse.success(result);
        } catch (BizException e) {
            log.warn("设备导入预估失败: errorCode={}, message={}", e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("设备导入预估失败: message={}", e.getMessage(), e);
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 执行设备导入
     *
     * @param reqDTO 导入请求（包含 sessionId 或 imeiList）
     * @return 导入结果
     */
    @PostMapping("/import/execute")
    @PreAuthorize("@rbac.has('fota:device:import')")
    public ApiResponse<DeviceImportRespDTO> executeImport(@RequestBody DeviceImportExecuteReqDTO reqDTO) {

        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("执行设备导入: productId={}, batchName={}, hasSessionId={}, hasImeis={}",
                    reqDTO.getProductId(), reqDTO.getBatchName(),
                    reqDTO.getSessionId() != null, reqDTO.getImeis() != null);
        }

        try {
            DeviceImportRespDTO result = deviceAppService.executeImportDevices(reqDTO);
            log.info("设备导入成功: batchId={}, totalCount={}, successCount={}, failedCount={}",
                    result.getBatchId(), result.getTotalCount(), result.getSuccessCount(), result.getFailedCount());
            return ApiResponse.success(result);
        } catch (BizException e) {
            log.warn("设备导入失败: productId={}, errorCode={}, message={}",
                    reqDTO.getProductId(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("设备导入失败: productId={}, message={}", reqDTO.getProductId(), e.getMessage(), e);
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "设备导入失败: " + e.getMessage());
        }
    }

    /**
     * 创建设备
     *
     * @param reqDTO 设备信息
     * @return 创建设备
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:device:import')")
    public ApiResponse<DeviceRespDTO> createDevice(@RequestBody DeviceReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建设备: imei={}, productId={}, currentVersionId={}, status={}",
                    reqDTO.getImei(), reqDTO.getProductId(), reqDTO.getVersionParts(), reqDTO.getStatus());
        }

        try {
            Device device = deviceAssembler.toDeviceEntity(reqDTO);
            device.setId(null);
            Device createdDevice = deviceAppService.createDevice(device);
            DeviceRespDTO deviceResp = fillName(createdDevice);
            log.info("设备创建成功: deviceId={}, imei={}", createdDevice.getId(), createdDevice.getImei());
            return ApiResponse.success(deviceResp);
        } catch (BizException e) {
            log.warn("创建设备失败: imei={}, errorCode={}, message={}",
                    reqDTO.getImei(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建设备参数错误: imei={}, message={}", reqDTO.getImei(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 更新设备信息
     *
     * @param id 设备 ID
     * @param reqDTO 设备信息
     * @return 更新后的设备
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:device:update')")
    public ApiResponse<DeviceRespDTO> updateDevice(@PathVariable Long id, @RequestBody DeviceReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新设备: deviceId={}", id);
        }

        try {
            Device device = deviceAssembler.toDeviceEntity(reqDTO);
            device.setId(id);
            Device updatedDevice = deviceAppService.updateDevice(device);

            DeviceRespDTO deviceResp = fillName(updatedDevice);
            log.info("设备更新成功: deviceId={}", updatedDevice.getId());
            return ApiResponse.success(deviceResp);
        } catch (BizException e) {
            log.warn("更新设备失败: deviceId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新设备参数错误: deviceId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    private DeviceRespDTO fillName(Device device) {
        DeviceNameContext nameContext = deviceDisplayNameService.resolveForDevice(device);
        return deviceAssembler.toDeviceResp(device, nameContext);
    }

    /**
     * 删除设备
     *
     * @param id 设备 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:device:update')")
    public ApiResponse<Void> deleteDevice(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除设备: deviceId={}", id);
        }

        try {
            deviceAppService.deleteDevice(id);
            log.info("设备删除成功: deviceId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除设备失败: deviceId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除设备参数错误: deviceId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 解析 IMEI 文件（用于批量操作等场景）
     *
     * @param file 导入文件（Excel或TXT）
     * @return 解析后的 IMEI 列表
     */
    @PostMapping("/parse-imei-file")
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<List<String>> parseImeiFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "导入文件不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("解析IMEI文件: filename={}", file.getOriginalFilename());
        }

        try {
            // 文件大小限制 10MB
            long maxSize = 10 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "文件大小超过10MB限制");
            }

            List<String> imeis = deviceAppService.parseImeiFile(file).getImeis();
            log.info("IMEI文件解析成功: filename={}, count={}", file.getOriginalFilename(), imeis.size());
            return ApiResponse.success(imeis);
        } catch (BizException e) {
            log.warn("IMEI文件解析失败: errorCode={}, message={}", e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("IMEI文件解析失败: message={}", e.getMessage(), e);
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 预估批量操作影响的设备数
     *
     * @param reqDTO 批量操作请求参数
     * @return 影响的设备数
     */
    @PostMapping("/batch/estimate")
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<Integer> estimateBatchOperation(@RequestBody BatchOperationReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "请求参数不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("预估批量操作: operationType={}", reqDTO.getOperationType());
        }

        try {
            reqDTO.validate();
            int count = deviceAppService.estimateBatchOperation(reqDTO);
            log.info("批量操作预估完成: operationType={}, count={}", reqDTO.getOperationType(), count);
            return ApiResponse.success(count);
        } catch (BizException e) {
            log.warn("批量操作预估失败: operationType={}, errorCode={}, message={}",
                    reqDTO.getOperationType(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("批量操作预估参数错误: operationType={}, message={}",
                    reqDTO.getOperationType(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 执行批量操作
     *
     * @param reqDTO 批量操作请求参数
     * @return 操作结果
     */
    @PostMapping("/batch/execute")
    @PreAuthorize("@rbac.has('fota:device:update')")
    public ApiResponse<BatchOperationResultDTO> executeBatchOperation(@RequestBody BatchOperationReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "请求参数不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("执行批量操作: operationType={}", reqDTO.getOperationType());
        }

        try {
            BatchOperationResultDTO result = deviceAppService.executeBatchOperation(reqDTO);
            log.info("批量操作执行完成: operationType={}, totalCount={}, successCount={}, failedCount={}",
                    reqDTO.getOperationType(), result.getTotalCount(), result.getSuccessCount(), result.getFailedCount());
            return ApiResponse.success(result);
        } catch (BizException e) {
            log.warn("批量操作执行失败: operationType={}, errorCode={}, message={}",
                    reqDTO.getOperationType(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("批量操作执行参数错误: operationType={}, message={}",
                    reqDTO.getOperationType(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
