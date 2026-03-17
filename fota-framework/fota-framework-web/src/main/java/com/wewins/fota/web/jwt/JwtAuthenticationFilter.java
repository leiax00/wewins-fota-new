package com.wewins.fota.web.jwt;

import com.wewins.fota.common.context.UserContext;
import com.wewins.fota.common.exception.TokenExpiredException;
import com.wewins.fota.security.jwt.JwtUtil;
import com.wewins.fota.security.jwt.SysUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * <p>
 * 从请求头中提取 JWT Token，验证并设置认证信息，并管理用户上下文生命周期。
 * </p>
 * <p>
 * <b>重要说明：</b>
 * <ul>
 *   <li>本过滤器在 finally 块中清理 UserContext，适用于同步请求</li>
 *   <li>如需支持异步请求（@Async、DeferredResult 等），需额外配置 AsyncListener 进行清理</li>
 *   <li>当前项目未使用 Servlet 异步特性，因此无需额外配置</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                    UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 提取 Token
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                try {
                    username = jwtUtil.getUsernameFromToken(token);
                } catch (TokenExpiredException e) {
                    // Token 已过期，继续处理但不对请求认证
                    if (log.isDebugEnabled()) {
                        log.debug("Token 已过期: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    log.warn("解析 Token 失败: {}", e.getMessage());
                }
            }

            // 如果有用户名且当前未认证，则进行认证
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 验证 Token 是否有效
                if (jwtUtil.validateToken(token)) {
                    // 创建认证对象
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置到 Security Context
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 设置 UserContext（用于审计）
                    // 检查是否是 SysUserDetails（带 userId 的 UserDetails）
                    if (userDetails instanceof SysUserDetails sysUserDetails) {
                        UserContext.setCurrentUserId(sysUserDetails.getUserId());
                    } else if (log.isDebugEnabled()) {
                        log.debug("UserDetails 不是 SysUserDetails 实例，跳过 userId 设置");
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("用户认证成功: username={}", username);
                    }
                }
            }
        } catch (Exception e) {
            log.error("认证过程发生异常: {}", e.getMessage(), e);
        } finally {
            try {
                filterChain.doFilter(request, response);
            } finally {
                // 清理 UserContext（避免线程池复用问题）
                UserContext.clear();
            }
        }
    }
}
