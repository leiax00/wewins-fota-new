package com.wewins.fota.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.dto.PageResponse;
import com.wewins.fota.module.system.dto.Response;
import com.wewins.fota.module.system.dto.UserPageReqVO;
import com.wewins.fota.module.system.entity.Role;
import com.wewins.fota.module.system.entity.User;
import com.wewins.fota.module.system.exception.BizException;
import com.wewins.fota.module.system.exception.ErrorCode;
import com.wewins.fota.module.system.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户 Controller
 * <p>
 * 提供用户管理的 CRUD 接口与角色分配功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/users")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户
     *
     * @param reqVO 分页查询参数
     * @return 分页用户列表
     */
    @GetMapping
    public Response<PageResponse<User>> listUsers(@ModelAttribute UserPageReqVO reqVO) {
        if (reqVO == null) {
            reqVO = new UserPageReqVO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询用户: status={}, page={}, size={}",
                    reqVO.getStatus(), reqVO.getPage(), reqVO.getSize());
        }

        Page<User> pageResult = userService.pageUsers(reqVO);

        // 清空密码哈希，避免暴露给前端
        pageResult.getRecords().forEach(user -> user.setPasswordHash(null));

        PageResponse<User> response = PageResponse.of(
                pageResult.getRecords(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return Response.success(response);
    }

    /**
     * 获取用户详情
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Response<User> getUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取用户详情: userId={}", id);
        }

        try {
            User user = userService.getUserById(id);
            if (user == null) {
                return Response.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            // 清空密码哈希，避免暴露给前端
            user.setPasswordHash(null);

            return Response.success(user);
        } catch (BizException e) {
            log.warn("获取用户详情失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取用户详情参数错误: userId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 创建后的用户
     */
    @PostMapping
    public Response<User> createUser(@RequestBody User user) {
        if (user == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建用户: username={}", user.getUsername());
        }

        try {
            user.setId(null);
            User createdUser = userService.createUser(user, null);

            // 清空密码哈希，避免暴露给前端
            createdUser.setPasswordHash(null);

            log.info("用户创建成功: userId={}, username={}", createdUser.getId(), createdUser.getUsername());
            return Response.success(createdUser);
        } catch (BizException e) {
            log.warn("创建用户失败: username={}, code={}, message={}", user.getUsername(), e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建用户参数错误: username={}, message={}", user.getUsername(), e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 更新用户
     *
     * @param id   用户ID
     * @param user 用户信息
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    public Response<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        if (id == null || id <= 0 || user == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新用户: userId={}", id);
        }

        try {
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return Response.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            // 密码字段不允许通过此接口修改，保留原始值
            user.setId(id);
            user.setPasswordHash(existingUser.getPasswordHash());

            User updatedUser = userService.updateUser(user, null);

            // 清空密码哈希，避免暴露给前端
            updatedUser.setPasswordHash(null);

            log.info("用户更新成功: userId={}", updatedUser.getId());
            return Response.success(updatedUser);
        } catch (BizException e) {
            log.warn("更新用户失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新用户参数错误: userId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 删除用户（软删除）
     *
     * @param id 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Response<Void> deleteUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除用户: userId={}", id);
        }

        try {
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return Response.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            userService.deleteUser(id);
            log.info("用户删除成功: userId={}", id);
            return Response.success();
        } catch (BizException e) {
            log.warn("删除用户失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除用户参数错误: userId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 获取用户角色
     *
     * @param id 用户ID
     * @return 角色列表
     */
    @GetMapping("/{id}/roles")
    public Response<List<Role>> getUserRoles(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取用户角色: userId={}", id);
        }

        try {
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return Response.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            List<Role> roles = userService.getUserRoles(id);
            return Response.success(roles);
        } catch (BizException e) {
            log.warn("获取用户角色失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取用户角色参数错误: userId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 分配角色
     *
     * @param id      用户ID
     * @param roleIds 角色ID列表
     * @return 分配结果
     */
    @PostMapping("/{id}/roles")
    public Response<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("分配角色: userId={}, roleIds={}", id, roleIds);
        }

        try {
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return Response.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            userService.assignRoles(id, roleIds);

            int roleCount = (roleIds == null || roleIds.isEmpty()) ? 0 : roleIds.size();
            if (roleCount == 0) {
                log.info("清空用户角色: userId={}", id);
            } else {
                log.info("角色分配成功: userId={}, roleCount={}", id, roleCount);
            }

            return Response.success();
        } catch (BizException e) {
            log.warn("分配角色失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("分配角色参数错误: userId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
