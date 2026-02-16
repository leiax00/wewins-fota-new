package com.wewins.fota.module.system.domain.repository.rbac;

import com.wewins.fota.module.system.domain.entity.rbac.RolePermission;

import java.util.List;

public interface RolePermissionRepository {

    long countByRoleId(Long roleId);

    long countByPermissionId(Long permissionId);

    void deleteByRoleId(Long roleId);

    void saveBatch(List<RolePermission> rolePermissions);

    List<RolePermission> findByRoleId(Long roleId);

    List<RolePermission> findByRoleIds(List<Long> roleIds);
}
