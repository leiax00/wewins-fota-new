package com.wewins.fota.adapter.api.admin.dto.firmware;

import jakarta.validation.constraints.NotBlank;

/**
 * 补传固件包请求 DTO。
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public record AttachPackageReqDTO(
        /**
         * 上传会话 ID
         */
        @NotBlank(message = "uploadSessionId 不能为空")
        String uploadSessionId
) {
}
