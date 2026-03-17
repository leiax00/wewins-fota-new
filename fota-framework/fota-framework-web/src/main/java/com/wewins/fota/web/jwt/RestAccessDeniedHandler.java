package com.wewins.fota.web.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 访问拒绝处理器
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        if (response.isCommitted()) {
            log.debug("响应已提交，跳过 403 写回: uri={}", request.getRequestURI());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("权限不足: uri={}, message={}",
                    request.getRequestURI(), accessDeniedException.getMessage());
        }

        String jsonResponse = "{\"code\":40301,\"message\":\"Forbidden\",\"data\":null}";

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
