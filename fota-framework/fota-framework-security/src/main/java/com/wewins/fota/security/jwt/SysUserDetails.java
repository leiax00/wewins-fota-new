package com.wewins.fota.security.jwt;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails 实现
 * <p>
 * 扩展标准的 UserDetails，增加用户ID和扩展字段
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Getter
public class SysUserDetails extends User {

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 显示名称
     */
    private final String displayName;

    /**
     * 邮箱
     */
    private final String email;

    public SysUserDetails(Long userId,
                          String username,
                          String password,
                          String displayName,
                          String email,
                          Collection<SimpleGrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
    }

    /**
     * 从用户实体创建 UserDetails
     *
     * @param userId      用户ID
     * @param username    用户名
     * @param password    密码哈希
     * @param displayName 显示名称
     * @param email       邮箱
     * @param permissions 权限编码列表
     * @return UserDetails
     */
    public static SysUserDetails create(Long userId,
                                        String username,
                                        String password,
                                        String displayName,
                                        String email,
                                        Collection<String> permissions) {
        Collection<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new SysUserDetails(userId, username, password, displayName, email, authorities);
    }
}
