package com.wewins.fota.domain.device.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.device.model.entity.Device;

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

    /**
     * 批量创建设备
     *
     * @param devices 设备列表
     */
    void batchCreate(List<Device> devices);

    /**
     * 分页查询批次下的设备
     *
     * @param page 分页对象
     * @param importBatchId 导入批次ID
     * @return 分页结果
     */
    Page<Device> pageByImportBatchId(Page<Device> page, Long importBatchId);

    /**
     * 查询批次下所有设备（用于批量操作）
     *
     * @param importBatchId 导入批次ID
     * @return 设备列表
     */
    List<Device> findAllByImportBatchId(Long importBatchId);

    /**
     * 根据IMEI列表查询设备（用于批量操作）
     *
     * @param imeis IMEI列表
     * @return 设备列表
     */
    List<Device> findByImeis(List<String> imeis);

    /**
     * 根据条件查询设备（用于批量操作预览）
     *
     * @param productId 产品ID（可选）
     * @param imeiKeyword IMEI关键词（可选）
     * @param status 设备状态（可选）
     * @param importBatchId 导入批次ID（可选）
     * @return 设备列表
     */
    List<Device> findByConditions(Long productId, String imeiKeyword, String status, Long importBatchId);

    /**
     * 批量更新设备标签
     *
     * @param deviceIds 设备ID列表
     * @param tagsJson 标签JSON字符串
     */
    void batchUpdateTags(List<Long> deviceIds, String tagsJson);

    /**
     * 批量更新设备批次ID
     *
     * @param deviceIds 设备ID列表
     * @param batchId 新批次ID
     */
    void batchUpdateImportBatchId(List<Long> deviceIds, Long batchId);

    /**
     * 批量软删除设备
     *
     * @param deviceIds 设备ID列表
     * @return 删除数量
     */
    int batchSoftDelete(List<Long> deviceIds);

    /**
     * 批量更新设备信息
     *
     * @param devices 设备列表
     */
    void updateBatch(List<Device> devices);
}
