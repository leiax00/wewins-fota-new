package com.wewins.fota.module.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典项响应 DTO
 */
@Data
@Builder
public class DictItemRespDTO {

    private Long id;
    private Long dictTypeId;
    private String label;
    private String value;
    private String i18nKey;
    private Integer sortOrder;
    private String status;
    private JsonNode extra;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
