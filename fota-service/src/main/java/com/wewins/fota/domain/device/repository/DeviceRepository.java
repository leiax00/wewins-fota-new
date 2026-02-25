package com.wewins.fota.domain.device.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.device.entity.Device;

import java.util.List;
import java.util.Optional;

/**
 * 设备仓储（领域端口）。
 */
public interface DeviceRepository {

    /**
     * 旧接口：按条件查询设备（列表）
     */
    List<Device> findByConditions(Long productId, String imei);

    /**
     * 分页查询设备
     */
    Page<Device> pageDevices(Page<Device> page, Long productId, String imei, String status);

    /**
     * 根据 ID 查询设备
     */
    Optional<Device> findById(Long id);

    /**
     * 根据 IMEI 查询设备
     */
    Optional<Device> findByImei(String imei);

    /**
     * 创建设备
     */
    Device create(Device device);

    /**
     * 更新设备
     */
    Device updateById(Device device);

    /**
     * IMEI 唯一性校验（排除指定 ID）
     */
    long countByImeiExcludingId(String imei, Long excludeId);

    /**
     * 逻辑删除设备
     */
    boolean softDeleteById(Long id);
}
