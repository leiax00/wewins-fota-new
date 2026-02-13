package com.wewins.fota.module.system.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.entity.rbac.Role;
import com.wewins.fota.module.system.domain.entity.rbac.RolePermission;
import com.wewins.fota.module.system.domain.repository.rbac.PermissionRepository;
import com.wewins.fota.module.system.domain.repository.rbac.RolePermissionRepository;
import com.wewins.fota.module.system.domain.repository.rbac.RoleRepository;
import com.wewins.fota.module.system.domain.repository.rbac.UserRoleRepository;
import com.wewins.fota.module.system.dto.RolePageReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleAppService  {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    public RoleAppService(RoleRepository roleRepository,
                           PermissionRepository permissionRepository,
                           RolePermissionRepository rolePermissionRepository,
                           UserRoleRepository userRoleRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public Role createRole(Role role, List<Long> permissionIds) {
        if (log.isDebugEnabled()) {
            log.debug("创建角色: code={}, permissionIds={}", role.getCode(), permissionIds);
        }

        validateRoleCodeUnique(role.getCode(), null);
        roleRepository.create(role);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            assignPermissions(role.getId(), permissionIds);
        }

        log.info("角色创建成功: roleId={}, code={}", role.getId(), role.getCode());
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public Role updateRole(Role role, List<Long> permissionIds) {
        if (role.getId() == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新角色: roleId={}, code={}, permissionIds={}", role.getId(), role.getCode(), permissionIds);
        }

        validateRoleCodeUnique(role.getCode(), role.getId());
        roleRepository.updateById(role);

        if (permissionIds != null) {
            assignPermissions(role.getId(), permissionIds);
        }

        log.info("角色更新成功: roleId={}", role.getId());
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除角色: roleId={}", roleId);
        }

        if (userRoleRepository.countByRoleId(roleId) > 0) {
            throw new IllegalStateException("存在用户关联该角色，无法删除");
        }

        if (rolePermissionRepository.countByRoleId(roleId) > 0) {
            throw new IllegalStateException("存在权限关联该角色，无法删除");
        }

        boolean result = roleRepository.deleteById(roleId);
        log.info("角色删除成功: roleId={}, result={}", roleId, result);
        return result;
    }

    public Role getRoleById(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }
        return roleRepository.findById(roleId);
    }

    public Page<Role> pageRoles(RolePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new RolePageReqDTO();
        }
        reqDTO.validate();
        return roleRepository.page(reqDTO);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("为角色分配权限: roleId={}, permissionIds={}", roleId, permissionIds);
        }

        Role role = roleRepository.findById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }

        rolePermissionRepository.deleteByRoleId(roleId);

        if (permissionIds == null || permissionIds.isEmpty()) {
            log.info("清空角色权限: roleId={}", roleId);
            return;
        }

        List<Long> distinctPermissionIds = permissionIds.stream().distinct().collect(Collectors.toList());
        validatePermissionIds(distinctPermissionIds);

        List<RolePermission> rolePermissions = distinctPermissionIds.stream()
                .map(permissionId -> RolePermission.builder().roleId(roleId).permissionId(permissionId).build())
                .toList();

        rolePermissionRepository.saveBatch(rolePermissions);
        log.info("角色权限分配成功: roleId={}, permissionCount={}", roleId, distinctPermissionIds.size());
    }

    public List<Permission> getRolePermissions(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(roleId);
        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> permissionIds = rolePermissions.stream().map(RolePermission::getPermissionId).distinct().collect(Collectors.toList());
        return permissionRepository.findByIds(permissionIds);
    }

    private void validateRoleCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }

        if (roleRepository.countByCodeExcludingId(code, excludeId) > 0) {
            throw new IllegalArgumentException("角色编码已存在: " + code);
        }
    }

    private void validatePermissionIds(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        long count = permissionRepository.countByIds(permissionIds);
        if (count != permissionIds.size()) {
            throw new IllegalArgumentException("部分权限不存在或已被删除");
        }
    }
}
