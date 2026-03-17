package com.wewins.fota.adapter.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.firmware.dto.FirmwareVersionReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionRespDTO;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 固件版本 DTO 转换器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirmwareVersionAssembler {

    private final ObjectMapper objectMapper;

    /**
     * 将 FirmwareVersionReqDTO 转换为 FirmwareVersion 实体
     */
    public FirmwareVersion toFirmwareVersionEntity(FirmwareVersionReqDTO req) {
        if (req == null) {
            return null;
        }

        FirmwareVersion.FirmwareVersionBuilder builder = FirmwareVersion.builder()
                .productId(req.getProductId())
                .version(req.getVersion())
                .internalVersion(req.getInternalVersion())
                .fileUrl(req.getFileUrl())
                .fileName(req.getFileName())
                .fileSize(req.getFileSize())
                .md5(req.getMd5())
                .sha256(req.getSha256())
                .packageStatus(req.getPackageStatus());

        if (req.getTags() != null && !req.getTags().isBlank()) {
            try {
                builder.tags(objectMapper.readValue(req.getTags(), Map.class));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("tags JSON 格式错误: " + e.getMessage(), e);
            }
        }

        if (req.getMeta() != null && !req.getMeta().isBlank()) {
            try {
                builder.meta(objectMapper.readValue(req.getMeta(), Map.class));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("meta JSON 格式错误: " + e.getMessage(), e);
            }
        }

        return builder.build();
    }

    /**
     * 将 FirmwareVersion 实体转换为 FirmwareVersionRespDTO
     */
    public FirmwareVersionRespDTO toFirmwareVersionResp(FirmwareVersion firmwareVersion) {
        return toFirmwareVersionResp(firmwareVersion, null);
    }

    /**
     * 将 FirmwareVersion 实体转换为 FirmwareVersionRespDTO（带产品名称）
     *
     * @param firmwareVersion 固件版本实体
     * @param productName     产品名称（可选）
     * @return 固件版本响应 DTO
     */
    public FirmwareVersionRespDTO toFirmwareVersionResp(FirmwareVersion firmwareVersion, String productName) {
        if (firmwareVersion == null) {
            return null;
        }

        FirmwareVersionRespDTO.FirmwareVersionRespDTOBuilder builder = FirmwareVersionRespDTO.builder()
                .id(firmwareVersion.getId())
                .productId(firmwareVersion.getProductId())
                .productName(productName)
                .version(firmwareVersion.getVersion())
                .internalVersion(firmwareVersion.getInternalVersion())
                .fileUrl(firmwareVersion.getFileUrl())
                .fileName(firmwareVersion.getFileName())
                .fileSize(firmwareVersion.getFileSize())
                .md5(firmwareVersion.getMd5())
                .sha256(firmwareVersion.getSha256())
                .packageStatus(firmwareVersion.getPackageStatus())
                .packageUploadedAt(firmwareVersion.getPackageUploadedAt())
                .createdAt(firmwareVersion.getCreatedAt())
                .createdBy(firmwareVersion.getCreatedBy())
                .updatedAt(firmwareVersion.getUpdatedAt())
                .updatedBy(firmwareVersion.getUpdatedBy());

        if (firmwareVersion.getTags() != null) {
            try {
                builder.tags(objectMapper.writeValueAsString(firmwareVersion.getTags()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("tags JSON 序列化失败: " + e.getMessage(), e);
            }
        }

        if (firmwareVersion.getMeta() != null) {
            try {
                builder.meta(objectMapper.writeValueAsString(firmwareVersion.getMeta()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("meta JSON 序列化失败: " + e.getMessage(), e);
            }
        }

        return builder.build();
    }
}
