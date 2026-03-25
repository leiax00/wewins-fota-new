package com.wewins.fota.domain.firmware.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;

import java.util.List;
import java.util.Map;
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
     * 根据 ID 列表批量查询版本号
     *
     * @param ids 固件版本 ID 列表
     * @return 版本ID到版本号的映射
     */
    Map<Long, String> findVersionNamesByIds(List<Long> ids);

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
     * 根据版本号和产品 ID 查询单个固件版本
     * <p>
     * 用于升级检查时的版本匹配，返回第一个匹配的版本
     * </p>
     *
     * @param versionNumber 版本号
     * @param productId 产品 ID
     * @return 固件版本（如果存在）
     */
    List<FirmwareVersion> findByVersionNumberAndProductId(String versionNumber, Long productId);

    /**
     * 根据版本号、内部版本号和产品 ID 查询固件版本
     * <p>
     * 用于精确匹配固件版本，当 version 号可能重复时，
     * 通过 internalVersion 进行区分
     * </p>
     *
     * @param versionNumber 版本号
     * @param internalVersion 内部版本号（build tag）
     * @param productId 产品 ID
     * @return 固件版本（如果存在）
     */
    List<FirmwareVersion> findByVersionAndInternalVersionAndProductId(
            String versionNumber,
            String internalVersion,
            Long productId
    );

    /**
     * 检查固件版本唯一组合是否已存在。
     * <p>
     * 唯一性由 productId + version + internalVersion + tags 完全一致决定。
     * 更新场景下如果实体带有 id，则自动排除自身。
     * </p>
     *
     * @param firmwareVersion 固件版本实体
     * @return 是否存在冲突
     */
    boolean existsByUnique(FirmwareVersion firmwareVersion);

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
