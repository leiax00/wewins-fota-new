package com.wewins.fota.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wewins.fota.system.dto.PermissionPageReqVO;
import com.wewins.fota.system.entity.Permission;
import com.wewins.fota.system.service.dto.PermissionTreeNode;

import java.util.List;

/**
 * 权限服务接口
 * <p>
 * 提供权限的 CRUD 操作、树形结构查询等功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
public interface IPermissionService extends IService<Permission> {

    /**
     * 创建权限
     *
     * @param permission 权限信息
     * @return 创建的权限
     */
    Permission createPermission(Permission permission);

    /**
     * 更新权限
     *
     * @param permission 权限信息
     * @return 更新后的权限
     */
    Permission updatePermission(Permission permission);

    /**
     * 删除权限（软删除）
     *
     * @param permissionId 权限ID
     * @return 是否删除成功
     */
    boolean deletePermission(Long permissionId);

    /**
     * 根据ID查询权限
     *
     * @param permissionId 权限ID
     * @return 权限信息
     */
    Permission getPermissionById(Long permissionId);

    /**
     * 分页查询权限
     *
     * @param reqVO 分页查询参数
     * @return 分页结果
     */
    Page<Permission> pagePermissions(PermissionPageReqVO reqVO);

    /**
     * 查询权限树
     *
     * @return 权限树列表
     */
    List<PermissionTreeNode> listPermissionTree();
}
