package com.wewins.fota.application.device.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.device.DeviceImportBatchAppService;
import com.wewins.fota.application.device.dto.DeviceImportBatchPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设备导入批次应用服务实现
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportBatchAppServiceImpl implements DeviceImportBatchAppService {

    private final DeviceImportBatchRepository deviceImportBatchRepository;

    @Override
    public Page<DeviceImportBatch> pageBatches(DeviceImportBatchPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DeviceImportBatchPageReqDTO();
        }
        reqDTO.validate();

        Page<DeviceImportBatch> page = new Page<>(reqDTO.getPage(), reqDTO.getSize());

        if (log.isDebugEnabled()) {
            log.debug("分页查询设备导入批次: batchName={}, status={}, page={}, size={}",
                    reqDTO.getBatchName(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        return deviceImportBatchRepository.pageBatches(page, reqDTO.getBatchName(), reqDTO.getStatus());
    }

    @Override
    public DeviceImportBatch getById(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "批次ID不能为空或小于0");
        }

        if (log.isDebugEnabled()) {
            log.debug("查询设备导入批次详情: batchId={}", id);
        }

        return deviceImportBatchRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND.getCode(), "批次不存在: " + id));
    }
}
