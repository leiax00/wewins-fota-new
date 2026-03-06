package com.wewins.fota.application.firmware;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.firmware.dto.FirmwareVersionPageReqDTO;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;

import java.util.List;

/**
 * 固件版本应用服务接口
 */
public interface FirmwareVersionAppService {

    /**
     * 分页查询固件版本列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    Page<FirmwareVersion> pageFirmwareVersions(FirmwareVersionPageReqDTO reqDTO);

    /**
     * 根据产品 ID 查询所有固件版本
     *
     * @param productId 产品 ID
     * @return 固件版本列表
     */
    List<FirmwareVersion> listByProductId(Long productId);

    /**
     * 根据 ID 获取固件版本
     *
     * @param id 固件版本 ID
     * @return 固件版本实体
     */
    FirmwareVersion getById(Long id);

    /**
     * 创建固件版本
     *
     * @param firmwareVersion 固件版本实体
     * @return 创建后的固件版本
     */
    FirmwareVersion createFirmwareVersion(FirmwareVersion firmwareVersion);

    /**
     * 更新固件版本
     *
     * @param firmwareVersion 固件版本实体
     * @return 更新后的固件版本
     */
    FirmwareVersion updateFirmwareVersion(FirmwareVersion firmwareVersion);

    /**
     * 删除固件版本
     *
     * @param id 固件版本 ID
     * @return 是否成功
     */
    boolean deleteFirmwareVersion(Long id);
}
