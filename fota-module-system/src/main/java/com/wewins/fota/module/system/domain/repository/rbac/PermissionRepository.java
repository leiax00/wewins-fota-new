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

    /**
     * 查询用户拥有的菜单权限（MODULE/MENU）
     * <p>
     * 业务约定：如果用户拥有子节点权限，则必定拥有父节点权限
     * 因此只需要查询用户直接拥有的 MODULE/MENU 权限，然后在内存中组织成树
     * </p>
     *
     * @param userId 用户ID
     * @return 菜单权限列表（MODULE/MENU 类型）
     */
    List<Permission> findMenuPermissionsByUserId(Long userId);
}
