package com.wewins.fota.module.system.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.entity.rbac.Role;
import com.wewins.fota.module.system.domain.entity.rbac.RolePermission;
import com.wewins.fota.module.system.domain.entity.rbac.UserRole;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.domain.repository.rbac.PermissionRepository;
import com.wewins.fota.module.system.domain.repository.rbac.RolePermissionRepository;
import com.wewins.fota.module.system.domain.repository.rbac.RoleRepository;
import com.wewins.fota.module.system.domain.repository.rbac.UserRoleRepository;
import com.wewins.fota.module.system.domain.repository.user.UserRepository;
import com.wewins.fota.module.system.dto.UserPageReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserAppService  {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public UserAppService(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PermissionRepository permissionRepository,
                           UserRoleRepository userRoleRepository,
                           RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public User createUser(User user, List<Long> roleIds) {
        if (log.isDebugEnabled()) {
            log.debug("创建用户: username={}, roleIds={}", user.getUsername(), roleIds);
        }

        validateUsernameUnique(user.getUsername(), null);
        userRepository.create(user);

        if (roleIds != null && !roleIds.isEmpty()) {
            assignRoles(user.getId(), roleIds);
        }

        log.info("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user, List<Long> roleIds) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新用户: userId={}, roleIds={}", user.getId(), roleIds);
        }

        validateUsernameUnique(user.getUsername(), user.getId());
        userRepository.updateById(user);

        if (roleIds != null) {
            assignRoles(user.getId(), roleIds);
        }

        log.info("用户更新成功: userId={}", user.getId());
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除用户: userId={}", userId);
        }

        userRoleRepository.deleteByUserId(userId);
        boolean result = userRepository.deleteById(userId);
        log.info("用户删除成功: userId={}, result={}", userId, result);
        return result;
    }

    public User getUserById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userRepository.findById(userId);
    }

    public User getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return userRepository.findFirstByUsername(username);
    }

    public List<User> listUsers(String keyword, String status) {
        return userRepository.findByKeywordAndStatus(keyword, status);
    }

    public Page<User> pageUsers(UserPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new UserPageReqDTO();
        }
        reqDTO.validate();
        return userRepository.page(reqDTO);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("为用户分配角色: userId={}, roleIds={}", userId, roleIds);
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        userRoleRepository.deleteByUserId(userId);

        if (roleIds == null || roleIds.isEmpty()) {
            log.info("清空用户角色: userId={}", userId);
            return;
        }

        List<Long> distinctRoleIds = roleIds.stream().distinct().collect(Collectors.toList());
        validateRoleIds(distinctRoleIds);

        List<UserRole> userRoles = distinctRoleIds.stream()
                .map(roleId -> UserRole.builder().userId(userId).roleId(roleId).build())
                .toList();

        userRoleRepository.saveBatch(userRoles);
        log.info("用户角色分配成功: userId={}, roleCount={}", userId, distinctRoleIds.size());
    }

    public List<Role> getUserRoles(Long userId) {
        List<Long> roleIds = getRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleRepository.findByIds(roleIds);
    }

    /**
     * 获取用户角色 code 列表
     *
     * @param userId 用户ID
     * @return 角色 code 列表
     */
    public List<String> getUserRoleCodes(Long userId) {
        if (log.isDebugEnabled()) {
            log.debug("查询用户角色编码: userId={}", userId);
        }

        List<Role> roles = getUserRoles(userId);
        return roles.stream()
                .map(Role::getCode)
                .toList();
    }

    /**
     * 获取用户权限 code 列表
     *
     * @param userId 用户ID
     * @return 权限 code 列表
     */
    public List<String> getUserPermissionCodes(Long userId) {
        if (log.isDebugEnabled()) {
            log.debug("查询用户权限编码: userId={}", userId);
        }
        List<Permission> permissions = getUserPermissions(userId);
        return permissions.stream().map(Permission::getCode).toList();
    }

    public List<Permission> getUserPermissions(Long userId) {
        List<Long> roleIds = getRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIds(roleIds);
        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> permissionIds = new HashSet<>();
        for (RolePermission rolePermission : rolePermissions) {
            permissionIds.add(rolePermission.getPermissionId());
        }

        return permissionRepository.findByIds(new ArrayList<>(permissionIds));
    }

    private void validateUsernameUnique(String username, Long excludeId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        long count = userRepository.countByUsernameExcludingId(username, excludeId);
        if (count > 0) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
    }

    private void validateRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        long count = roleRepository.countByIds(roleIds);
        if (count != roleIds.size()) {
            throw new IllegalArgumentException("部分角色不存在或已被删除");
        }
    }

    private List<Long> getRoleIds(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        return userRoles.stream().map(UserRole::getRoleId).distinct().collect(Collectors.toList());
    }
}
