package com.wewins.fota.domain.device.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.device.model.entity.Device;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository {

    List<Device> findByConditions(Long productId, String imei);

    Page<Device> pageDevices(Page<Device> page, Long productId, String imei, String status);

    Optional<Device> findById(Long id);

    Optional<Device> findByImei(String imei);

    Device create(Device device);

    Device updateById(Device device);

    long countByImeiExcludingId(String imei, Long excludeId);

    boolean softDeleteById(Long id);

    int batchDelete(List<Long> deviceIds);

    void updateBatch(List<Device> devices);

    void batchCreate(List<Device> devices);

    Page<Device> pageByImportBatchId(Page<Device> page, Long importBatchId);

    List<Device> findAllByImportBatchId(Long importBatchId);

    List<Device> findByImeis(List<String> imeis);

    List<Device> findByConditions(Long productId, String imeiKeyword, String status, Long importBatchId);

    void batchUpdateTags(List<Long> deviceIds, String tagsJson);

    void batchUpdateImportBatchId(List<Long> deviceIds, Long batchId);
}
