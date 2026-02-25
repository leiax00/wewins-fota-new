package com.wewins.fota.module.system.security;

import com.wewins.fota.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 权限校验工具类
 * <p>
 * 用于 Spring Security @PreAuthorize 注解的 SpEL 表达式调用
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-24
 */
@Slf4j
@Component("rbac")
@RequiredArgsConstructor
public class RbacExpressionService {

    /**
     * 通配符权限：超级管理员
     */
    private static final String WILDCARD_PERMISSION = "*:*:*";

    /**
     * 检查是否拥有指定权限
     *
     * @param permission 权限码（如：sys:user:read）
     * @return true if has permission
     */
    public boolean has(String permission) {
        Set<String> authorities = getCurrentAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        // 超级管理员检查
        if (authorities.contains(WILDCARD_PERMISSION)) {
            return true;
        }

        return authorities.contains(permission);
    }

    /**
     * 检查是否拥有任一权限
     *
     * @param permissions 权限码数组
     * @return true if has any permission
     */
    public boolean any(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }

        Set<String> authorities = getCurrentAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        // 超级管理员检查
        if (authorities.contains(WILDCARD_PERMISSION)) {
            return true;
        }

        for (String permission : permissions) {
            if (authorities.contains(permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否拥有所有权限
     *
     * @param permissions 权限码数组
     * @return true if has all permissions
     */
    public boolean all(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }

        Set<String> authorities = getCurrentAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        // 超级管理员检查
        if (authorities.contains(WILDCARD_PERMISSION)) {
            return true;
        }

        for (String permission : permissions) {
            if (!authorities.contains(permission)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查是否为当前用户本人
     *
     * @param userId 用户ID
     * @return true if is current user
     */
    public boolean isSelf(Long userId) {
        if (userId == null) {
            return false;
        }

        Long currentUserId = UserContext.getCurrentUserId();
        return userId.equals(currentUserId);
    }

    /**
     * 检查是否拥有权限或为本人
     *
     * @param permission 权限码
     * @param userId     用户ID
     * @return true if has permission or is self
     */
    public boolean hasOrSelf(String permission, Long userId) {
        return has(permission) || isSelf(userId);
    }

    /**
     * 获取当前用户的权限集合
     *
     * @return 权限码集合
     */
    private Set<String> getCurrentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
