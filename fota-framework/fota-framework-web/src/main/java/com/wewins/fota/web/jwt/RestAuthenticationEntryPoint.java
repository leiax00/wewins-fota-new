package com.wewins.fota.web.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证入口点
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        if (response.isCommitted()) {
            log.debug("响应已提交，跳过 401 写回: uri={}", request.getRequestURI());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("认证失败: uri={}, message={}",
                    request.getRequestURI(), authException.getMessage());
        }

        String jsonResponse = "{\"code\":40101,\"message\":\"Unauthorized\",\"data\":null}";

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
