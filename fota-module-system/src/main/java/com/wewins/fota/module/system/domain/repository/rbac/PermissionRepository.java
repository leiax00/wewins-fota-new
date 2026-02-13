package com.wewins.fota.module.system.domain.repository.rbac;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.dto.PermissionPageReqDTO;

import java.util.List;

public interface PermissionRepository {

    void create(Permission permission);

    boolean updateById(Permission permission);

    boolean deleteById(Long permissionId);

    Permission findById(Long permissionId);

    Page<Permission> page(PermissionPageReqDTO reqDTO);

    List<Permission> findAllOrderByParentIdAndId();

    long countByIds(List<Long> permissionIds);

    List<Permission> findByIds(List<Long> permissionIds);

    long countByCodeExcludingId(String code, Long excludeId);

    long countByParentId(Long parentId);
}
