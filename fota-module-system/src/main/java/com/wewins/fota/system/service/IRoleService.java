package com.wewins.fota.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.system.entity.Permission;
import com.wewins.fota.system.entity.Role;

import java.util.List;

/**
 * 角色服务接口
 * <p>
 * 提供角色的 CRUD 操作、权限分配等功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
public interface IRoleService extends IService<Role> {

    /**
     * 创建角色
     *
     * @param role 角色信息
     * @param permissionIds 权限ID列表（可选）
     * @return 创建的角色
     */
    Role createRole(Role role, List<Long> permissionIds);

    /**
     * 更新角色
     *
     * @param role 角色信息
     * @param permissionIds 权限ID列表（可选，传 null 则不修改权限）
     * @return 更新后的角色
     */
    Role updateRole(Role role, List<Long> permissionIds);

    /**
     * 删除角色（软删除）
     *
     * @param roleId 角色ID
     * @return 是否删除成功
     */
    boolean deleteRole(Long roleId);

    /**
     * 根据ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色信息
     */
    Role getRoleById(Long roleId);

    /**
     * 查询角色列表
     *
     * @param keyword 关键词（搜索编码、名称、描述）
     * @param status 状态（可选）
     * @return 角色列表
     */
    List<Role> listRoles(String keyword, String status);

    /**
     * 分页查询角色
     *
     * @param param 分页参数（含排序）
     * @return 分页结果
     */
    Page<Role> pageRoles(BaseRequestVo param);

    /**
     * 为角色分配权限
     *
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 获取角色的权限列表
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    List<Permission> getRolePermissions(Long roleId);
}
