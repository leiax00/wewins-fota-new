package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.domain.device.entity.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器
 * <p>
 * 提供设备管理接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/admin/device")
@ConditionalOnAppMode("main")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceAppService deviceAppService;

    /**
     * 获取设备列表
     *
     * @param productId 产品 ID（可选）
     * @param imei      设备 IMEI（可选）
     * @return 设备列表
     */
    @GetMapping
    public ApiResponse<List<Device>> listDevices(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String imei) {
        List<Device> devices = deviceAppService.listDevices(productId, imei);
        return ApiResponse.success(devices);
    }

    /**
     * 获取设备详情
     *
     * @param id 设备 ID
     * @return 设备详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Device> getDevice(@PathVariable Long id) {
        Device device = deviceAppService.getDevice(id);
        if (device == null) {
            return ApiResponse.error(404, "设备不存在");
        }
        return ApiResponse.success(device);
    }

    /**
     * 批量导入设备
     *
     * @param request 导入请求
     * @return 导入结果
     */
    @PostMapping("/import")
    public ApiResponse<String> importDevices(@RequestBody String request) {
        return ApiResponse.success(deviceAppService.importDevices(request));
    }

    /**
     * 更新设备信息
     *
     * @param id 设备 ID
     * @return 更新的设备
     */
    @PutMapping("/{id}")
    public ApiResponse<String> updateDevice(@PathVariable Long id) {
        return ApiResponse.success(deviceAppService.updateDevice(id));
    }

    /**
     * 删除设备
     *
     * @param id 设备 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDevice(@PathVariable Long id) {
        boolean deleted = deviceAppService.deleteDevice(id);
        if (!deleted) {
            return ApiResponse.error(404, "设备不存在");
        }
        return ApiResponse.success();
    }
}
