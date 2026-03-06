package com.wewins.fota.application.device;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.device.dto.DeviceImportBatchPageReqDTO;
import com.wewins.fota.domain.device.model.entity.DeviceImportBatch;

/**
 * 设备导入批次应用服务接口
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public interface DeviceImportBatchAppService {

    /**
     * 分页查询批次列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    Page<DeviceImportBatch> pageBatches(DeviceImportBatchPageReqDTO reqDTO);

    /**
     * 根据ID查询批次详情
     *
     * @param id 批次ID
     * @return 批次实体
     */
    DeviceImportBatch getById(Long id);
}
