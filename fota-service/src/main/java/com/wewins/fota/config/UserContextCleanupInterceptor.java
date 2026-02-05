package com.wewins.fota.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

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
@Component
public class UserContextCleanupInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserContextCleanupInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        // 不需要处理
    }

    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        // 请求结束后清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
        log.debug("请求完成，已清理用户上下文");
    }
}
