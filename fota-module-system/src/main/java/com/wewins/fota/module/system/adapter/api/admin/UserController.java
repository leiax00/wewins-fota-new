package com.wewins.fota.module.system.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.UserAppService;
import com.wewins.fota.module.system.application.assembler.AdminApiAssembler;
import com.wewins.fota.module.system.dto.RoleRespDTO;
import com.wewins.fota.module.system.dto.UserPageReqDTO;
import com.wewins.fota.module.system.dto.UserReqDTO;
import com.wewins.fota.module.system.dto.UserRespDTO;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/sys/users")
public class UserController {

    private final UserAppService userService;
    private final AdminApiAssembler adminApiAssembler;

    public UserController(UserAppService userService, AdminApiAssembler adminApiAssembler) {
        this.userService = userService;
        this.adminApiAssembler = adminApiAssembler;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserRespDTO>> listUsers(@ModelAttribute UserPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new UserPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询用户: status={}, page={}, size={}", reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<?> pageResult = userService.pageUsers(reqDTO);
        List<UserRespDTO> records = adminApiAssembler.toUserRespListFromUnknown(pageResult.getRecords());

        PageResponse<UserRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserRespDTO> getUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取用户详情: userId={}", id);
        }

        try {
            var user = userService.getUserById(id);
            if (user == null) {
                return ApiResponse.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(adminApiAssembler.toUserResp(user));
        } catch (BizException e) {
            log.warn("获取用户详情失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取用户详情参数错误: userId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<UserRespDTO> createUser(@RequestBody UserReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建用户: username={}", reqDTO.getUsername());
        }

        try {
            var user = adminApiAssembler.toUserEntity(reqDTO);
            user.setId(null);
            var createdUser = userService.createUser(user, null);

            log.info("用户创建成功: userId={}, username={}", createdUser.getId(), createdUser.getUsername());
            return ApiResponse.success(adminApiAssembler.toUserResp(createdUser));
        } catch (BizException e) {
            log.warn("创建用户失败: username={}, code={}, message={}", reqDTO.getUsername(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建用户参数错误: username={}, message={}", reqDTO.getUsername(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<UserRespDTO> updateUser(@PathVariable Long id, @RequestBody UserReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新用户: userId={}", id);
        }

        try {
            var existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return ApiResponse.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            var user = adminApiAssembler.toUserEntity(reqDTO);
            user.setId(id);
            user.setPasswordHash(existingUser.getPasswordHash());

            var updatedUser = userService.updateUser(user, null);
            log.info("用户更新成功: userId={}", updatedUser.getId());
            return ApiResponse.success(adminApiAssembler.toUserResp(updatedUser));
        } catch (BizException e) {
            log.warn("更新用户失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新用户参数错误: userId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除用户: userId={}", id);
        }

        try {
            var existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return ApiResponse.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            userService.deleteUser(id);
            log.info("用户删除成功: userId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除用户失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除用户参数错误: userId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @GetMapping("/{id}/roles")
    public ApiResponse<List<RoleRespDTO>> getUserRoles(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取用户角色: userId={}", id);
        }

        try {
            var existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return ApiResponse.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            var roles = userService.getUserRoles(id);
            return ApiResponse.success(adminApiAssembler.toRoleRespList(roles));
        } catch (BizException e) {
            log.warn("获取用户角色失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取用户角色参数错误: userId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("分配角色: userId={}, roleIds={}", id, roleIds);
        }

        try {
            var existingUser = userService.getUserById(id);
            if (existingUser == null) {
                return ApiResponse.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
            }

            userService.assignRoles(id, roleIds);

            int roleCount = (roleIds == null || roleIds.isEmpty()) ? 0 : roleIds.size();
            if (roleCount == 0) {
                log.info("清空用户角色: userId={}", id);
            } else {
                log.info("角色分配成功: userId={}, roleCount={}", id, roleCount);
            }

            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("分配角色失败: userId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("分配角色参数错误: userId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
