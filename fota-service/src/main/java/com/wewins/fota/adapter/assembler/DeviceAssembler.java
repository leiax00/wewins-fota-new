package com.wewins.fota.adapter.assembler;

import com.wewins.fota.application.device.support.DeviceNameContext;
import com.wewins.fota.application.device.dto.DeviceReqDTO;
import com.wewins.fota.application.device.dto.DeviceRespDTO;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 设备 DTO 转换器
 */
@Component
public class DeviceAssembler {

    /**
     * 将 DeviceReqDTO 转换为 Device 实体
     */
    public Device toDeviceEntity(DeviceReqDTO req) {
        if (req == null) {
            return null;
        }

        Device.DeviceBuilder builder = Device.builder()
                .imei(req.getImei())
                .productId(req.getProductId())
                .versionParts(req.getVersionParts())
                .status(req.getStatus())
                .tags(req.getTags());

        return builder.build();
    }

    /**
     * 将 Device 实体转换为 DeviceRespDTO（含关联名称，4参数版本）
     */
    public DeviceRespDTO toDeviceResp(
            Device device,
            DeviceNameContext nameContext
    ) {
        if (device == null) {
            return null;
        }

        String productName = nameContext.productNameMap().get(device.getProductId());
        Map<Long, String> versionNameMap = nameContext.versionNameMap();
        String importBatchName = nameContext.batchNameMap().get(device.getImportBatchId());

        DeviceVersionParts versionParts = device.getVersionParts();
        if (versionParts != null && versionParts.hasVersion()) {
            versionParts.getParts().values().forEach(item -> {
                Long versionId = item.getVersionId();
                item.setVersion(versionNameMap.get(versionId));
            });
        }
        DeviceVersionParts initialVersionParts = device.getInitialVersionParts();
        if (initialVersionParts != null && initialVersionParts.hasVersion()) {
            initialVersionParts.getParts().values().forEach(item -> {
                Long versionId = item.getVersionId();
                item.setVersion(versionNameMap.get(versionId));
            });
        }

        DeviceRespDTO.DeviceRespDTOBuilder builder = DeviceRespDTO.builder()
                .id(device.getId())
                .imei(device.getImei())
                .productId(device.getProductId())
                .productName(productName)
                .versionParts(versionParts)
                .initialVersionParts(initialVersionParts)
                .status(device.getStatus())
                .lastSeenAt(device.getLastSeenAt())
                .firstSeenAt(device.getFirstSeenAt())
                .importBatchId(device.getImportBatchId())
                .importBatchName(importBatchName)
                .createdAt(device.getCreatedAt())
                .createdBy(device.getCreatedBy())
                .updatedAt(device.getUpdatedAt())
                .updatedBy(device.getUpdatedBy());

        builder.tags(device.getTags());

        return builder.build();
    }
}
