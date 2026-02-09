package com.wewins.fota.security.context;

import com.wewins.fota.common.context.UserContext;
import com.wewins.fota.security.jwt.SysUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 用户上下文工具类
 * <p>
 * 从 Spring Security 的 {@link SecurityContextHolder} 获取当前用户信息，
 * 同时保留 ThreadLocal 作为备选方案（用于异步场景等）。
 * </p>
 *
 * <p>
 * 优先级：
 * <ol>
 *   <li>从 Spring Security SecurityContextHolder 获取（优先）</li>
 *   <li>从 ThreadLocal 获取（用于异步场景）</li>
 * </ol>
 * </p>
 *
 * <p>
 * 使用场景：
 * <ul>
 *   <li>在业务代码中，通过 getCurrentUserId() 获取当前用户ID</li>
 *   <li>通过 getCurrentUsername() 获取当前用户名</li>
 *   <li>通过 isAuthenticated() 判断用户是否已认证</li>
 *   <li>在异步线程中，通过 UserContext.runWithUser() 设置用户上下文</li>
 * </ul>
 * </p>
 *
 * <p>
 * 示例代码：
 * </p>
 * <pre>{@code
 * // 获取当前用户ID
 * Long userId = SecurityUserContext.getCurrentUserId();
 *
 * // 获取当前用户名
 * String username = SecurityUserContext.getCurrentUsername();
 *
 * // 判断是否已认证
 * if (SecurityUserContext.isAuthenticated()) {
 *     // 已认证用户的业务逻辑
 * }
 *
 * // 在异步场景中使用
 * UserContext.runWithUser(userId, () -> {
 *     // 异步任务代码
 * });
 * }</pre>
 *
 * @author FOTA Team
 * @since 2026-02-09
 * @see UserContext
 */
@Slf4j
public class SecurityUserContext {

    /**
     * 私有构造函数，防止实例化
     */
    private SecurityUserContext() {
    }

    /**
     * 获取当前用户ID
     * <p>
     * 优先级：
     * <ol>
     *   <li>从 Spring Security SecurityContextHolder 获取（优先）</li>
     *   <li>从 ThreadLocal 获取（用于异步场景）</li>
     * </ol>
     * </p>
     *
     * @return 当前用户ID，如果未设置则返回 null
     */
    public static Long getCurrentUserId() {
        // 优先从 Spring Security 获取
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();

            // 从 SysUserDetails 获取 userId
            if (principal instanceof SysUserDetails sysUserDetails) {
                Long userId = sysUserDetails.getUserId();
                if (log.isDebugEnabled()) {
                    log.debug("获取当前用户ID（从 Spring Security）: {}", userId);
                }
                return userId;
            }

            // 尝试从其他 UserDetails 实现获取（通过反射，兼容其他实现）
            try {
                Object userId = principal.getClass().getMethod("getUserId").invoke(principal);
                if (userId instanceof Long) {
                    if (log.isDebugEnabled()) {
                        log.debug("获取当前用户ID（从 Spring Security 反射）: {}", userId);
                    }
                    return (Long) userId;
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        // 从 ThreadLocal 获取（用于异步场景）
        Long userId = UserContext.getCurrentUserId();
        if (userId != null && log.isDebugEnabled()) {
            log.debug("获取当前用户ID（从 ThreadLocal）: {}", userId);
        }
        return userId;
    }

    /**
     * 获取当前用户ID，如果未设置则返回默认值
     *
     * @param defaultValue 默认值
     * @return 当前用户ID，如果未设置则返回默认值
     */
    public static Long getCurrentUserIdOrDefault(Long defaultValue) {
        Long userId = getCurrentUserId();
        return userId != null ? userId : defaultValue;
    }

    /**
     * 获取当前用户ID，如果未设置则抛出异常
     *
     * @return 当前用户ID
     * @throws IllegalStateException 如果用户未认证
     */
    public static Long requireCurrentUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("当前用户未认证");
        }
        return userId;
    }

    /**
     * 获取当前用户名
     * <p>
     * 从 Spring Security SecurityContextHolder 获取
     * </p>
     *
     * @return 当前用户名，如果未设置则返回 null
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            String username = authentication.getName();
            if (log.isDebugEnabled()) {
                log.debug("获取当前用户名: {}", username);
            }
            return username;
        }
        return null;
    }

    /**
     * 获取当前认证信息
     *
     * @return 当前认证信息，如果未设置则返回 null
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 判断当前用户是否已认证
     *
     * @return true 如果已认证，false 否则
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * 判断当前用户是否拥有指定角色
     *
     * @param role 角色代码（如 "ROLE_ADMIN"）
     * @return true 如果拥有该角色，false 否则
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(role));
        }
        return false;
    }

    /**
     * 判断当前用户是否拥有指定权限
     *
     * @param permission 权限代码（如 "sys:user:read"）
     * @return true 如果拥有该权限，false 否则
     */
    public static boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(permission));
        }
        return false;
    }

    /**
     * 清除所有上下文（包括 Spring Security 和 ThreadLocal）
     * <p>
     * 注意：谨慎使用，会清除 Spring Security 的 SecurityContextHolder。
     * </p>
     */
    public static void clearAll() {
        if (log.isDebugEnabled()) {
            log.debug("清除所有用户上下文");
        }
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }
}
