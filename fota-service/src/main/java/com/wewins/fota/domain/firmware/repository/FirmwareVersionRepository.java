package com.wewins.fota.domain.firmware.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;

import java.util.List;
import java.util.Optional;

/**
 * 固件版本仓储（领域端口）。
 */
public interface FirmwareVersionRepository {

    Optional<FirmwareVersion> findById(Long id);

    /**
     * 根据 ID 列表批量查询固件版本
     *
     * @param ids 固件版本 ID 列表
     * @return 固件版本列表
     */
    List<FirmwareVersion> listByIds(List<Long> ids);

    /**
     * 根据产品 ID 查询所有固件版本
     *
     * @param productId 产品 ID
     * @return 固件版本列表（按版本号降序）
     */
    List<FirmwareVersion> findByProductIdOrderByVersionDesc(Long productId);

    /**
     * 根据产品 ID 和版本号查询固件版本
     *
     * @param productId 产品 ID
     * @param version 版本号
     * @return 固件版本列表
     */
    List<FirmwareVersion> findByProductIdAndVersion(Long productId, String version);

    /**
     * 分页查询固件版本
     *
     * @param page 分页参数
     * @param productId 产品 ID（可选）
     * @param version 版本号（模糊查询，可选）
     * @return 分页结果
     */
    Page<FirmwareVersion> pageFirmwareVersions(Page<FirmwareVersion> page, Long productId, String version);

    /**
     * 创建固件版本
     *
     * @param firmwareVersion 固件版本实体
     * @return 创建后的固件版本
     */
    FirmwareVersion create(FirmwareVersion firmwareVersion);

    /**
     * 更新固件版本
     *
     * @param firmwareVersion 固件版本实体
     * @return 更新后的固件版本
     */
    FirmwareVersion updateById(FirmwareVersion firmwareVersion);

    /**
     * 删除固件版本（逻辑删除）
     *
     * @param id 固件版本 ID
     * @return 是否成功
     */
    boolean deleteById(Long id);
}
