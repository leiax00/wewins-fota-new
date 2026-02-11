package com.wewins.fota.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.database.wrapper.LambdaQueryWrapperX;
import com.wewins.fota.system.entity.Permission;
import com.wewins.fota.system.entity.Role;
import com.wewins.fota.system.entity.RolePermission;
import com.wewins.fota.system.entity.UserRole;
import com.wewins.fota.system.mapper.PermissionMapper;
import com.wewins.fota.system.mapper.RoleMapper;
import com.wewins.fota.system.mapper.RolePermissionMapper;
import com.wewins.fota.system.mapper.UserRoleMapper;
import com.wewins.fota.system.service.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Slf4j
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements IRoleService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleServiceImpl(PermissionMapper permissionMapper,
                           RolePermissionMapper rolePermissionMapper,
                           UserRoleMapper userRoleMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role createRole(Role role, List<Long> permissionIds) {
        if (log.isDebugEnabled()) {
            log.debug("创建角色: code={}, permissionIds={}", role.getCode(), permissionIds);
        }

        validateRoleCodeUnique(role.getCode(), null);
        save(role);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            assignPermissions(role.getId(), permissionIds);
        }

        log.info("角色创建成功: roleId={}, code={}", role.getId(), role.getCode());
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role updateRole(Role role, List<Long> permissionIds) {
        if (role.getId() == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新角色: roleId={}, code={}, permissionIds={}",
                    role.getId(), role.getCode(), permissionIds);
        }

        validateRoleCodeUnique(role.getCode(), role.getId());
        updateById(role);

        if (permissionIds != null) {
            assignPermissions(role.getId(), permissionIds);
        }

        log.info("角色更新成功: roleId={}", role.getId());
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除角色: roleId={}", roleId);
        }

        // 检查是否有用户关联该角色
        Long userRefCount = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, roleId));

        if (userRefCount != null && userRefCount > 0) {
            throw new IllegalStateException("存在用户关联该角色，无法删除");
        }

        // 检查是否有权限关联该角色
        Long permRefCount = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));

        if (permRefCount != null && permRefCount > 0) {
            throw new IllegalStateException("存在权限关联该角色，无法删除");
        }

        boolean result = removeById(roleId);
        log.info("角色删除成功: roleId={}, result={}", roleId, result);
        return result;
    }

    @Override
    public Role getRoleById(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }
        return getById(roleId);
    }

    @Override
    public List<Role> listRoles(String keyword, String status) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Role::getCode, keyword)
                    .or()
                    .like(Role::getName, keyword)
                    .or()
                    .like(Role::getDescription, keyword));
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Role::getStatus, status);
        }

        wrapper.orderByDesc(Role::getId);
        return list(wrapper);
    }

    @Override
    public Page<Role> pageRoles(BaseRequestVo param) {
        if (param == null) {
            param = new BaseRequestVo();
        }
        param.validate();

        LambdaQueryWrapperX<Role> wrapper = new LambdaQueryWrapperX<>(Role.class)
                .likeAnyIfPresent(param.getKeyword(),
                        "code", "name", "description")
                .applyFiltersIfPresent(param.getFilters())
                .applySortingIfPresent(param.getSortingFields());

        return page(new Page<>(param.getPage(), param.getSize()), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("为角色分配权限: roleId={}, permissionIds={}", roleId, permissionIds);
        }

        Role role = getById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }

        // 删除旧的角色权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));

        if (permissionIds == null || permissionIds.isEmpty()) {
            log.info("清空角色权限: roleId={}", roleId);
            return;
        }

        // 验证权限是否存在（先去重）
        List<Long> distinctPermissionIds = permissionIds.stream()
                .distinct()
                .collect(Collectors.toList());
        validatePermissionIds(distinctPermissionIds);

        // 批量插入新的角色权限关联
        List<RolePermission> rolePermissions = distinctPermissionIds.stream()
                .map(permissionId -> RolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .build())
                .collect(Collectors.toList());

        for (RolePermission rolePermission : rolePermissions) {
            rolePermissionMapper.insert(rolePermission);
        }

        log.info("角色权限分配成功: roleId={}, permissionCount={}", roleId, distinctPermissionIds.size());
    }

    @Override
    public List<Permission> getRolePermissions(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));

        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> permissionIds = rolePermissions.stream()
                .map(RolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());

        return permissionMapper.selectBatchIds(permissionIds);
    }

    /**
     * 验证角色编码唯一性
     *
     * @param code 角色编码
     * @param excludeId 排除的角色ID
     */
    private void validateRoleCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, code);

        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }

        Long count = baseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("角色编码已存在: " + code);
        }
    }

    /**
     * 验证权限ID列表
     * <p>
     * 注意：需先对 permissionIds 去重后再调用此方法
     * </p>
     *
     * @param permissionIds 权限ID列表（已去重）
     */
    private void validatePermissionIds(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        Long count = permissionMapper.selectCount(new LambdaQueryWrapper<Permission>()
                .in(Permission::getId, permissionIds));

        if (count == null || count != permissionIds.size()) {
            throw new IllegalArgumentException("部分权限不存在或已被删除");
        }
    }
}
