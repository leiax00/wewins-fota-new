package com.wewins.fota.module.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDTO {

    /**
     * JWT Token
     */
    private String token;

    /**
     * Token 类型（固定为 Bearer）
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 显示名称
     */
    private String displayName;
}
