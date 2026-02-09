package com.wewins.fota.security.jwt;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置属性
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * JWT 密钥（至少 32 字符）
     */
    @NotBlank(message = "JWT 密钥不能为空")
    @Size(min = 32, message = "JWT 密钥长度至少为 32 字符")
    private String secret;

    /**
     * Token 过期时间（秒）
     */
    private Long expireSeconds = 7200L;

    /**
     * 默认密钥（仅用于检测，不允许使用）
     */
    private static final String DEFAULT_SECRET = "wewins-fota-jwt-secret-key-change-in-production-2026";

    /**
     * 验证 JWT 密钥配置
     */
    @PostConstruct
    public void validateSecret() {
        if (DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT 密钥必须通过环境变量设置，不能使用默认值。请设置环境变量: JWT_SECRET"
            );
        }
    }
}
