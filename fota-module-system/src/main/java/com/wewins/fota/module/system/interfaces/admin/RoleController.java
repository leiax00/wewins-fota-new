package com.wewins.fota.module.system.interfaces.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.dto.PageResponse;
import com.wewins.fota.module.system.dto.Response;
import com.wewins.fota.module.system.dto.RolePageReqDTO;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.entity.rbac.Role;
import com.wewins.fota.module.system.application.exception.BizException;
import com.wewins.fota.module.system.application.exception.ErrorCode;
import com.wewins.fota.module.system.application.RoleAppService;
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
 * 角色 Controller
 * <p>
 * 提供角色管理的 CRUD 接口与权限分配功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/roles")
public class RoleController {

    private final RoleAppService roleService;

    public RoleController(RoleAppService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查询角色
     *
     * @param reqVO 分页查询参数
     * @return 分页角色列表
     */
    @GetMapping
    public Response<PageResponse<Role>> listRoles(@ModelAttribute RolePageReqDTO reqVO) {
        if (reqVO == null) {
            reqVO = new RolePageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询角色: status={}, page={}, size={}",
                    reqVO.getStatus(), reqVO.getPage(), reqVO.getSize());
        }

        Page<Role> pageResult = roleService.pageRoles(reqVO);

        PageResponse<Role> response = PageResponse.of(
                pageResult.getRecords(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return Response.success(response);
    }

    /**
     * 获取角色详情
     *
     * @param id 角色ID
     * @return 角色信息
     */
    @GetMapping("/{id}")
    public Response<Role> getRole(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取角色详情: roleId={}", id);
        }

        try {
            Role role = roleService.getRoleById(id);
            if (role == null) {
                return Response.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }
            return Response.success(role);
        } catch (BizException e) {
            log.warn("获取角色详情失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取角色详情参数错误: roleId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 创建角色
     *
     * @param role 角色信息
     * @return 创建后的角色
     */
    @PostMapping
    public Response<Role> createRole(@RequestBody Role role) {
        if (role == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建角色: code={}", role.getCode());
        }

        try {
            role.setId(null);
            Role createdRole = roleService.createRole(role, null);

            log.info("角色创建成功: roleId={}, code={}", createdRole.getId(), createdRole.getCode());
            return Response.success(createdRole);
        } catch (BizException e) {
            log.warn("创建角色失败: code={}, errorCode={}, message={}", role.getCode(), e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建角色参数错误: code={}, message={}", role.getCode(), e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 更新角色
     *
     * @param id   角色ID
     * @param role 角色信息
     * @return 更新后的角色
     */
    @PutMapping("/{id}")
    public Response<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        if (id == null || id <= 0 || role == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新角色: roleId={}", id);
        }

        try {
            Role existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return Response.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            role.setId(id);
            Role updatedRole = roleService.updateRole(role, null);

            log.info("角色更新成功: roleId={}", updatedRole.getId());
            return Response.success(updatedRole);
        } catch (BizException e) {
            log.warn("更新角色失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新角色参数错误: roleId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 删除角色
     *
     * @param id 角色ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Response<Void> deleteRole(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除角色: roleId={}", id);
        }

        try {
            Role existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return Response.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            roleService.deleteRole(id);
            log.info("角色删除成功: roleId={}", id);
            return Response.success();
        } catch (BizException e) {
            log.warn("删除角色失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除角色参数错误: roleId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 获取角色权限
     *
     * @param id 角色ID
     * @return 权限列表
     */
    @GetMapping("/{id}/permissions")
    public Response<List<Permission>> getRolePermissions(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取角色权限: roleId={}", id);
        }

        try {
            Role existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return Response.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            List<Permission> permissions = roleService.getRolePermissions(id);
            return Response.success(permissions);
        } catch (BizException e) {
            log.warn("获取角色权限失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取角色权限参数错误: roleId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 分配权限
     *
     * @param id           角色ID
     * @param permissionIds 权限ID列表
     * @return 分配结果
     */
    @PostMapping("/{id}/permissions")
    public Response<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("分配权限: roleId={}, permissionIds={}", id, permissionIds);
        }

        try {
            Role existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return Response.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            roleService.assignPermissions(id, permissionIds);

            int permissionCount = (permissionIds == null || permissionIds.isEmpty()) ? 0 : permissionIds.size();
            if (permissionCount == 0) {
                log.info("清空角色权限: roleId={}", id);
            } else {
                log.info("权限分配成功: roleId={}, permissionCount={}", id, permissionCount);
            }

            return Response.success();
        } catch (BizException e) {
            log.warn("分配权限失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("分配权限参数错误: roleId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
