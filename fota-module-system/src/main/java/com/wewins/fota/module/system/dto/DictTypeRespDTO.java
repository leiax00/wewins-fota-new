package com.wewins.fota.module.system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型响应 DTO
 */
@Data
@Builder
public class DictTypeRespDTO {

    private Long id;
    private String code;
    private String name;
    private String i18nKey;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
