package com.wewins.fota.module.system.dto;

import lombok.Data;

/**
 * 字典类型创建/更新请求 DTO
 */
@Data
public class DictTypeReqDTO {

    private String code;
    private String name;
    private String i18nKey;
    private String status;
    private String description;
}
