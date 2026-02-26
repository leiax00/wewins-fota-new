package com.wewins.fota.domain.device.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;

import java.util.Optional;

/**
 * 设备导入批次仓储（领域端口）
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public interface DeviceImportBatchRepository {

    /**
     * 根据ID查询批次
     *
     * @param id 批次ID
     * @return 批次实体
     */
    Optional<DeviceImportBatch> findById(Long id);

    /**
     * 分页查询批次列表
     *
     * @param page 分页对象
     * @param batchName 批次名称（模糊查询）
     * @param status 批次状态
     * @return 分页结果
     */
    Page<DeviceImportBatch> pageBatches(Page<DeviceImportBatch> page, String batchName, String status);
}
