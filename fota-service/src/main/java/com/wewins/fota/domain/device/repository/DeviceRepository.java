package com.wewins.fota.domain.device.repository;

import com.wewins.fota.domain.device.entity.Device;

import java.util.Optional;

/**
 * 设备仓储（领域端口）。
 */
public interface DeviceRepository {

    Optional<Device> findByImei(String imei);
}
