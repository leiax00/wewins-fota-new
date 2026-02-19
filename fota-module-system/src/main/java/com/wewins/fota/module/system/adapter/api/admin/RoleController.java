package com.wewins.fota.module.system.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.RoleAppService;
import com.wewins.fota.module.system.application.assembler.AdminApiAssembler;
import com.wewins.fota.module.system.dto.PermissionRespDTO;
import com.wewins.fota.module.system.dto.RolePageReqDTO;
import com.wewins.fota.module.system.dto.RoleReqDTO;
import com.wewins.fota.module.system.dto.RoleRespDTO;
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
 * 角色 Controller
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/sys/roles")
public class RoleController {

    private final RoleAppService roleService;
    private final AdminApiAssembler adminApiAssembler;

    public RoleController(RoleAppService roleService, AdminApiAssembler adminApiAssembler) {
        this.roleService = roleService;
        this.adminApiAssembler = adminApiAssembler;
    }

    @GetMapping
    public ApiResponse<PageResponse<RoleRespDTO>> listRoles(@ModelAttribute RolePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new RolePageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询角色: status={}, page={}, size={}", reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<?> pageResult = roleService.pageRoles(reqDTO);
        List<RoleRespDTO> records = adminApiAssembler.toRoleRespListFromUnknown(pageResult.getRecords());

        PageResponse<RoleRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleRespDTO> getRole(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取角色详情: roleId={}", id);
        }

        try {
            var role = roleService.getRoleById(id);
            if (role == null) {
                return ApiResponse.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(adminApiAssembler.toRoleResp(role));
        } catch (BizException e) {
            log.warn("获取角色详情失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取角色详情参数错误: roleId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<RoleRespDTO> createRole(@RequestBody RoleReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建角色: code={}", reqDTO.getCode());
        }

        try {
            var role = adminApiAssembler.toRoleEntity(reqDTO);
            role.setId(null);
            var createdRole = roleService.createRole(role, null);

            log.info("角色创建成功: roleId={}, code={}", createdRole.getId(), createdRole.getCode());
            return ApiResponse.success(adminApiAssembler.toRoleResp(createdRole));
        } catch (BizException e) {
            log.warn("创建角色失败: code={}, errorCode={}, message={}", reqDTO.getCode(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建角色参数错误: code={}, message={}", reqDTO.getCode(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleRespDTO> updateRole(@PathVariable Long id, @RequestBody RoleReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新角色: roleId={}", id);
        }

        try {
            var existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return ApiResponse.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            var role = adminApiAssembler.toRoleEntity(reqDTO);
            role.setId(id);
            var updatedRole = roleService.updateRole(role, null);

            log.info("角色更新成功: roleId={}", updatedRole.getId());
            return ApiResponse.success(adminApiAssembler.toRoleResp(updatedRole));
        } catch (BizException e) {
            log.warn("更新角色失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新角色参数错误: roleId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除角色: roleId={}", id);
        }

        try {
            var existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return ApiResponse.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            roleService.deleteRole(id);
            log.info("角色删除成功: roleId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除角色失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除角色参数错误: roleId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<PermissionRespDTO>> getRolePermissions(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取角色权限: roleId={}", id);
        }

        try {
            var existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return ApiResponse.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            var permissions = roleService.getRolePermissions(id);
            return ApiResponse.success(adminApiAssembler.toPermissionRespList(permissions));
        } catch (BizException e) {
            log.warn("获取角色权限失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取角色权限参数错误: roleId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("分配权限: roleId={}, permissionIds={}", id, permissionIds);
        }

        try {
            var existingRole = roleService.getRoleById(id);
            if (existingRole == null) {
                return ApiResponse.error(ErrorCode.ROLE_NOT_FOUND.getCode(), ErrorCode.ROLE_NOT_FOUND.getMessage());
            }

            roleService.assignPermissions(id, permissionIds);
            int permissionCount = (permissionIds == null || permissionIds.isEmpty()) ? 0 : permissionIds.size();
            if (permissionCount == 0) {
                log.info("清空角色权限: roleId={}", id);
            } else {
                log.info("权限分配成功: roleId={}, permissionCount={}", id, permissionCount);
            }

            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("分配权限失败: roleId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("分配权限参数错误: roleId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
