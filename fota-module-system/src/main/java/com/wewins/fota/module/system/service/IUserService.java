package com.wewins.fota.module.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wewins.fota.module.system.dto.UserPageReqVO;
import com.wewins.fota.module.system.entity.Permission;
import com.wewins.fota.module.system.entity.Role;
import com.wewins.fota.module.system.entity.User;

import java.util.List;

/**
 * 用户服务接口
 * <p>
 * 提供用户的 CRUD 操作、角色分配、权限查询等功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
public interface IUserService extends IService<User> {

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @param roleIds 角色ID列表（可选）
     * @return 创建的用户
     */
    User createUser(User user, List<Long> roleIds);

    /**
     * 更新用户
     *
     * @param user 用户信息
     * @param roleIds 角色ID列表（可选，传 null 则不修改角色）
     * @return 更新后的用户
     */
    User updateUser(User user, List<Long> roleIds);

    /**
     * 删除用户（软删除）
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteUser(Long userId);

    /**
     * 根据ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    User getUserById(Long userId);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User getUserByUsername(String username);

    /**
     * 查询用户列表
     *
     * @param keyword 关键词（搜索用户名、显示名、邮箱、手机号）
     * @param status 状态（可选）
     * @return 用户列表
     */
    List<User> listUsers(String keyword, String status);

    /**
     * 分页查询用户
     * <p>
     * 使用类型安全的 ReqVO 进行查询，支持状态过滤、时间范围查询、排序
     * </p>
     *
     * @param reqVO 分页查询参数
     * @return 分页结果
     */
    Page<User> pageUsers(UserPageReqVO reqVO);

    /**
     * 为用户分配角色
     *
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 获取用户的角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> getUserRoles(Long userId);

    /**
     * 获取用户的权限列表
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    List<Permission> getUserPermissions(Long userId);
}
