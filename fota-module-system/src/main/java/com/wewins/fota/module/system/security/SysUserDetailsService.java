package com.wewins.fota.module.system.security;

import com.wewins.fota.module.system.entity.Permission;
import com.wewins.fota.module.system.entity.User;
import com.wewins.fota.module.system.service.IUserService;
import com.wewins.fota.security.jwt.SysUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetailsService 实现
 * <p>
 * 从数据库加载用户和权限信息
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Slf4j
@Service
public class SysUserDetailsService implements UserDetailsService {

    private final IUserService userService;

    public SysUserDetailsService(IUserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (log.isDebugEnabled()) {
            log.debug("加载用户信息: username={}", username);
        }

        // 查询用户
        User user = userService.getUserByUsername(username);
        if (user == null) {
            log.warn("用户不存在: username={}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 检查用户状态
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            log.warn("用户状态异常: username={}, status={}", username, user.getStatus());
            throw new UsernameNotFoundException("用户状态异常: " + user.getStatus());
        }

        // 查询用户权限
        List<Permission> permissions = userService.getUserPermissions(user.getId());
        List<String> permissionCodes = permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("用户权限加载成功: username={}, permissionCount={}", username, permissionCodes.size());
        }

        // 创建 UserDetails
        return SysUserDetails.create(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getEmail(),
                permissionCodes
        );
    }
}
