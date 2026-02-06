package com.wewins.fota.security.web;

import com.wewins.fota.common.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 用户上下文清理拦截器
 * <p>
 * 在请求结束后自动清理 ThreadLocal 中的用户上下文，防止内存泄漏。
 * </p>
 *
 * <p>
 * 使用方式：
 * <pre>
 * &#64;Configuration
 * public class WebMvcConfig implements WebMvcConfigurer {
 *     &#64;Autowired
 *     private UserContextCleanupInterceptor userContextCleanupInterceptor;
 *
 *     &#64;Override
 *     public void addInterceptors(InterceptorRegistry registry) {
 *         registry.addInterceptor(userContextCleanupInterceptor)
 *                 .addPathPatterns("/**");
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Slf4j
@Component
public class UserContextCleanupInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               @Nullable Exception ex) {
        // 请求结束后清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
        log.debug("请求完成，已清理用户上下文");
    }
}
