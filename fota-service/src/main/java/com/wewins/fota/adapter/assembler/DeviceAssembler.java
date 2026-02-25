package com.wewins.fota.adapter.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.device.dto.DeviceReqDTO;
import com.wewins.fota.application.device.dto.DeviceRespDTO;
import com.wewins.fota.domain.device.entity.Device;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 设备 DTO 转换器
 */
@Component
@RequiredArgsConstructor
public class DeviceAssembler {

    private final ObjectMapper objectMapper;

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
                .currentVersionId(req.getCurrentVersionId())
                .status(req.getStatus());

        if (req.getTags() != null && !req.getTags().isBlank()) {
            try {
                builder.tags(objectMapper.readTree(req.getTags()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("tags JSON 格式错误: " + e.getMessage(), e);
            }
        }

        return builder.build();
    }

    /**
     * 将 Device 实体转换为 DeviceRespDTO（不含关联名称）
     */
    public DeviceRespDTO toDeviceResp(Device device) {
        return toDeviceResp(device, null, null);
    }

    /**
     * 将 Device 实体转换为 DeviceRespDTO（含关联名称）
     */
    public DeviceRespDTO toDeviceResp(Device device, String productName, String versionName) {
        if (device == null) {
            return null;
        }

        DeviceRespDTO.DeviceRespDTOBuilder builder = DeviceRespDTO.builder()
                .id(device.getId())
                .imei(device.getImei())
                .productId(device.getProductId())
                .productName(productName)
                .currentVersionId(device.getCurrentVersionId())
                .versionName(versionName)
                .status(device.getStatus())
                .lastSeenAt(device.getLastSeenAt())
                .importBatchId(device.getImportBatchId())
                .createdAt(device.getCreatedAt())
                .createdBy(device.getCreatedBy())
                .updatedAt(device.getUpdatedAt())
                .updatedBy(device.getUpdatedBy());

        if (device.getTags() != null) {
            builder.tags(device.getTags().toString());
        }

        return builder.build();
    }
}
