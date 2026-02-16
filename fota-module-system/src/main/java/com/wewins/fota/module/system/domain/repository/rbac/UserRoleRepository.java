package com.wewins.fota.module.system.domain.repository.rbac;

import com.wewins.fota.module.system.domain.entity.rbac.UserRole;

import java.util.List;

public interface UserRoleRepository {

    long countByRoleId(Long roleId);

    void deleteByUserId(Long userId);

    void saveBatch(List<UserRole> userRoles);

    List<UserRole> findByUserId(Long userId);
}
