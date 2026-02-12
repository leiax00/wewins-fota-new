package com.wewins.fota.adapter.api.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // TODO: 注入需要的应用服务

    /**
     * 获取设备列表
     *
     * @param productId 产品 ID（可选）
     * @param imei 设备 IMEI（可选）
     * @return 设备列表
     */
    @GetMapping
    public ResponseEntity<String> listDevices(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String imei) {
        // TODO: 实现设备列表查询
        return ResponseEntity.ok("设备列表查询待实现");
    }

    /**
     * 获取设备详情
     *
     * @param id 设备 ID
     * @return 设备详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<String> getDevice(@PathVariable Long id) {
        // TODO: 实现设备详情查询
        return ResponseEntity.ok("设备详情查询待实现");
    }

    /**
     * 批量导入设备
     *
     * @param request 导入请求
     * @return 导入结果
     */
    @PostMapping("/import")
    public ResponseEntity<String> importDevices(@RequestBody String request) {
        // TODO: 实现设备批量导入
        return ResponseEntity.ok("设备导入待实现");
    }

    /**
     * 更新设备信息
     *
     * @param id 设备 ID
     * @return 更新的设备
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> updateDevice(@PathVariable Long id) {
        // TODO: 实现设备更新
        return ResponseEntity.ok("设备更新待实现");
    }

    /**
     * 删除设备
     *
     * @param id 设备 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        // TODO: 实现设备删除
        return ResponseEntity.noContent().build();
    }
}
