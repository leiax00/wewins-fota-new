package com.wewins.fota.module.system.infra.persistence.mybatis.repository.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.module.system.domain.entity.rbac.RolePermission;
import com.wewins.fota.module.system.domain.repository.rbac.RolePermissionRepository;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac.RolePermissionMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class RolePermissionRepositoryImpl implements RolePermissionRepository {

    private final RolePermissionMapper rolePermissionMapper;

    public RolePermissionRepositoryImpl(RolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public long countByRoleId(Long roleId) {
        Long count = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        return count == null ? 0L : count;
    }

    @Override
    public long countByPermissionId(Long permissionId) {
        Long count = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getPermissionId, permissionId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
    }

    @Override
    public void saveBatch(List<RolePermission> rolePermissions) {
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            return;
        }
        for (RolePermission rolePermission : rolePermissions) {
            rolePermissionMapper.insert(rolePermission);
        }
    }

    @Override
    public List<RolePermission> findByRoleId(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
    }

    @Override
    public List<RolePermission> findByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                .in(RolePermission::getRoleId, roleIds));
    }
}
