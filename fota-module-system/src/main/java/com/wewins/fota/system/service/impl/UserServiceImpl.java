package com.wewins.fota.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wewins.fota.system.entity.Permission;
import com.wewins.fota.system.entity.Role;
import com.wewins.fota.system.entity.RolePermission;
import com.wewins.fota.system.entity.User;
import com.wewins.fota.system.entity.UserRole;
import com.wewins.fota.system.mapper.PermissionMapper;
import com.wewins.fota.system.mapper.RoleMapper;
import com.wewins.fota.system.mapper.RolePermissionMapper;
import com.wewins.fota.system.mapper.UserMapper;
import com.wewins.fota.system.mapper.UserRoleMapper;
import com.wewins.fota.system.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public UserServiceImpl(RoleMapper roleMapper,
                           PermissionMapper permissionMapper,
                           UserRoleMapper userRoleMapper,
                           RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(User user, List<Long> roleIds) {
        if (log.isDebugEnabled()) {
            log.debug("创建用户: username={}, roleIds={}", user.getUsername(), roleIds);
        }

        validateUsernameUnique(user.getUsername(), null);
        save(user);

        if (roleIds != null && !roleIds.isEmpty()) {
            assignRoles(user.getId(), roleIds);
        }

        log.info("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user, List<Long> roleIds) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新用户: userId={}, roleIds={}", user.getId(), roleIds);
        }

        validateUsernameUnique(user.getUsername(), user.getId());
        updateById(user);

        if (roleIds != null) {
            assignRoles(user.getId(), roleIds);
        }

        log.info("用户更新成功: userId={}", user.getId());
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除用户: userId={}", userId);
        }

        // 先删除用户角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        boolean result = removeById(userId);
        log.info("用户删除成功: userId={}, result={}", userId, result);
        return result;
    }

    @Override
    public User getUserById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return getById(userId);
    }

    @Override
    public User getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        // 使用 last("LIMIT 1") 确保只返回一条，避免多行数据异常
        return list(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<User> listUsers(String keyword, String status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or()
                    .like(User::getDisplayName, keyword)
                    .or()
                    .like(User::getEmail, keyword)
                    .or()
                    .like(User::getPhone, keyword));
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status);
        }

        wrapper.orderByDesc(User::getId);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("为用户分配角色: userId={}, roleIds={}", userId, roleIds);
        }

        User user = getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 删除旧的用户角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        if (roleIds == null || roleIds.isEmpty()) {
            log.info("清空用户角色: userId={}", userId);
            return;
        }

        // 验证角色是否存在（先去重）
        List<Long> distinctRoleIds = roleIds.stream()
                .distinct()
                .collect(Collectors.toList());
        validateRoleIds(distinctRoleIds);

        // 批量插入新的用户角色关联
        List<UserRole> userRoles = distinctRoleIds.stream()
                .map(roleId -> UserRole.builder()
                        .userId(userId)
                        .roleId(roleId)
                        .build())
                .collect(Collectors.toList());

        for (UserRole userRole : userRoles) {
            userRoleMapper.insert(userRole);
        }

        log.info("用户角色分配成功: userId={}, roleCount={}", userId, distinctRoleIds.size());
    }

    @Override
    public List<Role> getUserRoles(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());

        return roleMapper.selectByIds(roleIds);
    }

    @Override
    public List<Permission> getUserPermissions(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 获取用户的角色
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取角色的ID列表
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());

        // 获取角色权限关联
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds));

        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取权限ID列表（去重）
        Set<Long> permissionIds = new HashSet<>();
        for (RolePermission rolePermission : rolePermissions) {
            permissionIds.add(rolePermission.getPermissionId());
        }

        return permissionMapper.selectBatchIds(new ArrayList<>(permissionIds));
    }

    /**
     * 验证用户名唯一性
     *
     * @param username 用户名
     * @param excludeId 排除的用户ID
     */
    private void validateUsernameUnique(String username, Long excludeId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);

        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }

        Long count = baseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
    }

    /**
     * 验证角色ID列表
     * <p>
     * 注意：需先对 roleIds 去重后再调用此方法
     * </p>
     *
     * @param roleIds 角色ID列表（已去重）
     */
    private void validateRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .in(Role::getId, roleIds));

        if (count == null || count != roleIds.size()) {
            throw new IllegalArgumentException("部分角色不存在或已被删除");
        }
    }
}
