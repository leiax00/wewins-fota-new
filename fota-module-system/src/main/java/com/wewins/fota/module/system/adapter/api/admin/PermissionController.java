package com.wewins.fota.module.system.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.PermissionAppService;
import com.wewins.fota.module.system.application.assembler.AdminApiAssembler;
import com.wewins.fota.module.system.dto.PermissionPageReqDTO;
import com.wewins.fota.module.system.dto.PermissionReqDTO;
import com.wewins.fota.module.system.dto.PermissionRespDTO;
import com.wewins.fota.module.system.dto.PermissionTreeNodeRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 权限 Controller
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/sys/permissions")
public class PermissionController {

    private final PermissionAppService permissionService;
    private final AdminApiAssembler adminApiAssembler;

    public PermissionController(PermissionAppService permissionService, AdminApiAssembler adminApiAssembler) {
        this.permissionService = permissionService;
        this.adminApiAssembler = adminApiAssembler;
    }

    @GetMapping
    @PreAuthorize("@rbac.has('sys:perm:read')")
    public ApiResponse<PageResponse<PermissionRespDTO>> listPermissions(@ModelAttribute PermissionPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new PermissionPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询权限: type={}, status={}, page={}, size={}",
                    reqDTO.getType(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<?> pageResult = permissionService.pagePermissions(reqDTO);
        List<PermissionRespDTO> records = adminApiAssembler.toPermissionRespListFromUnknown(pageResult.getRecords());

        PageResponse<PermissionRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('sys:perm:read')")
    public ApiResponse<PermissionRespDTO> getPermission(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取权限详情: permissionId={}", id);
        }

        try {
            var permission = permissionService.getPermissionById(id);
            if (permission == null) {
                return ApiResponse.error(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ErrorCode.PERMISSION_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(adminApiAssembler.toPermissionResp(permission));
        } catch (BizException e) {
            log.warn("获取权限详情失败: permissionId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取权限详情参数错误: permissionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("@rbac.has('sys:perm:create')")
    public ApiResponse<PermissionRespDTO> createPermission(@RequestBody PermissionReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建权限: code={}, parentId={}", reqDTO.getCode(), reqDTO.getParentId());
        }

        try {
            var permission = adminApiAssembler.toPermissionEntity(reqDTO);
            permission.setId(null);
            var createdPermission = permissionService.createPermission(permission);
            log.info("权限创建成功: permissionId={}, code={}", createdPermission.getId(), createdPermission.getCode());
            return ApiResponse.success(adminApiAssembler.toPermissionResp(createdPermission));
        } catch (BizException e) {
            log.warn("创建权限失败: code={}, errorCode={}, message={}", reqDTO.getCode(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建权限参数错误: code={}, message={}", reqDTO.getCode(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('sys:perm:update')")
    public ApiResponse<PermissionRespDTO> updatePermission(@PathVariable Long id, @RequestBody PermissionReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新权限: permissionId={}", id);
        }

        try {
            var existingPermission = permissionService.getPermissionById(id);
            if (existingPermission == null) {
                return ApiResponse.error(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ErrorCode.PERMISSION_NOT_FOUND.getMessage());
            }

            var permission = adminApiAssembler.toPermissionEntity(reqDTO);
            permission.setId(id);
            var updatedPermission = permissionService.updatePermission(permission);

            log.info("权限更新成功: permissionId={}", updatedPermission.getId());
            return ApiResponse.success(adminApiAssembler.toPermissionResp(updatedPermission));
        } catch (BizException e) {
            log.warn("更新权限失败: permissionId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新权限参数错误: permissionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has('sys:perm:delete')")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除权限: permissionId={}", id);
        }

        try {
            var existingPermission = permissionService.getPermissionById(id);
            if (existingPermission == null) {
                return ApiResponse.error(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ErrorCode.PERMISSION_NOT_FOUND.getMessage());
            }

            permissionService.deletePermission(id);
            log.info("权限删除成功: permissionId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除权限失败: permissionId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除权限参数错误: permissionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @GetMapping("/tree")
    @PreAuthorize("@rbac.has('sys:perm:read')")
    public ApiResponse<List<PermissionTreeNodeRespDTO>> listPermissionTree() {
        if (log.isDebugEnabled()) {
            log.debug("查询权限树");
        }

        try {
            var tree = permissionService.listPermissionTree();
            return ApiResponse.success(adminApiAssembler.toPermissionTreeNodeRespList(tree));
        } catch (BizException e) {
            log.warn("查询权限树失败: code={}, message={}", e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("查询权限树参数错误: message={}", e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
