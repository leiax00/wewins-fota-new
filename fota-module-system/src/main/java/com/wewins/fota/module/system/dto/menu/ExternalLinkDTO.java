package com.wewins.fota.module.system.dto.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外链配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalLinkDTO {

    /**
     * 外链 URL
     */
    private String url;

    /**
     * 打开方式（_self/_blank）
     */
    private String openMode;
}
