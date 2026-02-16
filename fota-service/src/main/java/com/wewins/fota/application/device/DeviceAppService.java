package com.wewins.fota.application.device;

import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备应用服务
 * <p>
 * 承接管理端设备请求，编排设备领域能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAppService {

    private final DeviceRepository deviceRepository;

    public List<Device> listDevices(Long productId, String imei) {
        log.debug("查询设备列表: productId={}, imei={}", productId, imei);
        return deviceRepository.findByConditions(productId, imei);
    }

    public Device getDevice(Long id) {
        log.debug("查询设备详情: id={}", id);
        return deviceRepository.findById(id).orElse(null);
    }

    public String importDevices(String request) {
        log.debug("批量导入设备: payloadLength={}", request == null ? 0 : request.length());
        // TODO: 实现设备批量导入（批次创建、解析、入库）
        return "设备导入待实现";
    }

    public String updateDevice(Long id) {
        log.debug("更新设备: id={}", id);
        // TODO: 实现设备更新
        return "设备更新待实现";
    }

    public boolean deleteDevice(Long id) {
        log.debug("删除设备: id={}", id);
        return deviceRepository.softDeleteById(id);
    }
}
