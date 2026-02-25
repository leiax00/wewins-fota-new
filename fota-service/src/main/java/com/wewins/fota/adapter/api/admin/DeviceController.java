package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.DeviceAssembler;
import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.application.device.dto.DeviceReqDTO;
import com.wewins.fota.application.device.dto.DeviceRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;

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

        Set<Long> productIds = devices.stream()
                .map(Device::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> versionIds = devices.stream()
                .map(Device::getCurrentVersionId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, String> productNameMap = productIds.stream()
                .map(productRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Product::getId, Product::getName));

        Map<Long, String> versionNameMap = versionIds.stream()
                .map(firmwareVersionRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(FirmwareVersion::getId, FirmwareVersion::getVersion));

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
            String productName = productRepository.findById(device.getProductId())
                    .map(Product::getName)
                    .orElse(null);
            String versionName = null;
            if (device.getCurrentVersionId() != null) {
                versionName = firmwareVersionRepository.findById(device.getCurrentVersionId())
                        .map(FirmwareVersion::getVersion)
                        .orElse(null);
            }
            return ApiResponse.success(deviceAssembler.toDeviceResp(device, productName, versionName));
        } catch (BizException e) {
            log.warn("获取设备详情失败: deviceId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取设备详情参数错误: deviceId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
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
                    reqDTO.getImei(), reqDTO.getProductId(), reqDTO.getCurrentVersionId(), reqDTO.getStatus());
        }

        try {
            Device device = deviceAssembler.toDeviceEntity(reqDTO);
            device.setId(null);
            Device createdDevice = deviceAppService.createDevice(device);
            String productName = productRepository.findById(createdDevice.getProductId())
                    .map(Product::getName)
                    .orElse(null);
            String versionName = null;
            if (createdDevice.getCurrentVersionId() != null) {
                versionName = firmwareVersionRepository.findById(createdDevice.getCurrentVersionId())
                        .map(FirmwareVersion::getVersion)
                        .orElse(null);
            }
            log.info("设备创建成功: deviceId={}, imei={}", createdDevice.getId(), createdDevice.getImei());
            return ApiResponse.success(deviceAssembler.toDeviceResp(createdDevice, productName, versionName));
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
            String productName = productRepository.findById(updatedDevice.getProductId())
                    .map(Product::getName)
                    .orElse(null);
            String versionName = null;
            if (updatedDevice.getCurrentVersionId() != null) {
                versionName = firmwareVersionRepository.findById(updatedDevice.getCurrentVersionId())
                        .map(FirmwareVersion::getVersion)
                        .orElse(null);
            }
            log.info("设备更新成功: deviceId={}", updatedDevice.getId());
            return ApiResponse.success(deviceAssembler.toDeviceResp(updatedDevice, productName, versionName));
        } catch (BizException e) {
            log.warn("更新设备失败: deviceId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新设备参数错误: deviceId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
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
}
