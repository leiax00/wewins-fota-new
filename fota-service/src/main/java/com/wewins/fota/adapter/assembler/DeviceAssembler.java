package com.wewins.fota.adapter.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.device.dto.DeviceReqDTO;
import com.wewins.fota.application.device.dto.DeviceRespDTO;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.value.DeviceVersionParts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

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
                .versionParts(req.getVersionParts())
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
     * 将 Device 实体转换为 DeviceRespDTO（含关联名称，4参数版本）
     */
    public DeviceRespDTO toDeviceResp(
            Device device,
            String productName,
            Map<Long, String> versionNameMap,
            String importBatchName
    ) {
        if (device == null) {
            return null;
        }
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

        if (device.getTags() != null) {
            builder.tags(device.getTags().toString());
        }

        return builder.build();
    }
}
