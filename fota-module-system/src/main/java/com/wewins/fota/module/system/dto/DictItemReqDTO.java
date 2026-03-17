package com.wewins.fota.module.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 字典项创建/更新请求 DTO
 */
@Data
public class DictItemReqDTO {

    private Long dictTypeId;
    private String label;
    private String value;
    private String i18nKey;
    private Integer sortOrder;
    private String status;
    private JsonNode extra;
}
