package com.wewins.fota.domain.device.repository;

import com.wewins.fota.domain.device.entity.Device;

import java.util.List;
import java.util.Optional;

/**
 * 设备仓储（领域端口）。
 */
public interface DeviceRepository {

    List<Device> findByConditions(Long productId, String imei);

    Optional<Device> findById(Long id);

    Optional<Device> findByImei(String imei);

    boolean softDeleteById(Long id);
}
