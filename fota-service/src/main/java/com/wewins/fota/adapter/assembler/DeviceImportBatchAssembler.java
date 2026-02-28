package com.wewins.fota.adapter.assembler;

import com.wewins.fota.application.device.dto.DeviceImportBatchRespDTO;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import org.springframework.stereotype.Component;

/**
 * 设备导入批次 DTO 转换器
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Component
public class DeviceImportBatchAssembler {

    /**
     * 将 DeviceImportBatch 实体转换为 DeviceImportBatchRespDTO
     *
     * @param batch 批次实体
     * @return 响应 DTO
     */
    public DeviceImportBatchRespDTO toDeviceImportBatchResp(DeviceImportBatch batch) {
        return toDeviceImportBatchResp(batch, null);
    }

    /**
     * 将 DeviceImportBatch 实体转换为 DeviceImportBatchRespDTO
     *
     * @param batch 批次实体
     * @param productName 产品名称（可选）
     * @return 响应 DTO
     */
    public DeviceImportBatchRespDTO toDeviceImportBatchResp(DeviceImportBatch batch, String productName) {
        if (batch == null) {
            return null;
        }
        return DeviceImportBatchRespDTO.builder()
                .id(batch.getId())
                .batchName(batch.getBatchName())
                .productId(batch.getProductId())
                .productName(productName)
                .status(batch.getStatus())
                .totalCount(batch.getTotalCount())
                .successCount(batch.getSuccessCount())
                .failedCount(batch.getFailedCount())
                .errorMessage(batch.getErrorMessage())
                .startedAt(batch.getStartedAt())
                .finishedAt(batch.getFinishedAt())
                .createdAt(batch.getCreatedAt())
                .createdBy(batch.getCreatedBy())
                .updatedAt(batch.getUpdatedAt())
                .updatedBy(batch.getUpdatedBy())
                .build();
    }
}
