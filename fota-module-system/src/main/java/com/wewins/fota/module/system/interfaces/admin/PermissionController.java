package com.wewins.fota.module.system.interfaces.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.dto.PageResponse;
import com.wewins.fota.module.system.dto.PermissionPageReqDTO;
import com.wewins.fota.module.system.dto.Response;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.application.exception.BizException;
import com.wewins.fota.module.system.application.exception.ErrorCode;
import com.wewins.fota.module.system.application.PermissionAppService;
import com.wewins.fota.module.system.application.dto.PermissionTreeNode;
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
 * 权限 Controller
 * <p>
 * 提供权限管理的 CRUD 接口与树形结构查询功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/permissions")
public class PermissionController {

    private final PermissionAppService permissionService;

    public PermissionController(PermissionAppService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 分页查询权限
     *
     * @param reqVO 分页查询参数
     * @return 分页权限列表
     */
    @GetMapping
    public Response<PageResponse<Permission>> listPermissions(@ModelAttribute PermissionPageReqDTO reqVO) {
        if (reqVO == null) {
            reqVO = new PermissionPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询权限: type={}, status={}, page={}, size={}",
                    reqVO.getType(), reqVO.getStatus(), reqVO.getPage(), reqVO.getSize());
        }

        Page<Permission> pageResult = permissionService.pagePermissions(reqVO);

        PageResponse<Permission> response = PageResponse.of(
                pageResult.getRecords(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return Response.success(response);
    }

    /**
     * 获取权限详情
     *
     * @param id 权限ID
     * @return 权限信息
     */
    @GetMapping("/{id}")
    public Response<Permission> getPermission(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取权限详情: permissionId={}", id);
        }

        try {
            Permission permission = permissionService.getPermissionById(id);
            if (permission == null) {
                return Response.error(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ErrorCode.PERMISSION_NOT_FOUND.getMessage());
            }
            return Response.success(permission);
        } catch (BizException e) {
            log.warn("获取权限详情失败: permissionId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取权限详情参数错误: permissionId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 创建权限
     *
     * @param permission 权限信息
     * @return 创建后的权限
     */
    @PostMapping
    public Response<Permission> createPermission(@RequestBody Permission permission) {
        if (permission == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建权限: code={}, parentId={}", permission.getCode(), permission.getParentId());
        }

        try {
            permission.setId(null);
            Permission createdPermission = permissionService.createPermission(permission);
            log.info("权限创建成功: permissionId={}, code={}", createdPermission.getId(), createdPermission.getCode());
            return Response.success(createdPermission);
        } catch (BizException e) {
            log.warn("创建权限失败: code={}, errorCode={}, message={}", permission.getCode(), e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建权限参数错误: code={}, message={}", permission.getCode(), e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 更新权限
     *
     * @param id         权限ID
     * @param permission 权限信息
     * @return 更新后的权限
     */
    @PutMapping("/{id}")
    public Response<Permission> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        if (id == null || id <= 0 || permission == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新权限: permissionId={}", id);
        }

        try {
            Permission existingPermission = permissionService.getPermissionById(id);
            if (existingPermission == null) {
                return Response.error(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ErrorCode.PERMISSION_NOT_FOUND.getMessage());
            }

            permission.setId(id);
            Permission updatedPermission = permissionService.updatePermission(permission);

            log.info("权限更新成功: permissionId={}", updatedPermission.getId());
            return Response.success(updatedPermission);
        } catch (BizException e) {
            log.warn("更新权限失败: permissionId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新权限参数错误: permissionId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 删除权限
     *
     * @param id 权限ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Response<Void> deletePermission(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除权限: permissionId={}", id);
        }

        try {
            Permission existingPermission = permissionService.getPermissionById(id);
            if (existingPermission == null) {
                return Response.error(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ErrorCode.PERMISSION_NOT_FOUND.getMessage());
            }

            permissionService.deletePermission(id);
            log.info("权限删除成功: permissionId={}", id);
            return Response.success();
        } catch (BizException e) {
            log.warn("删除权限失败: permissionId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除权限参数错误: permissionId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 获取权限树
     *
     * @return 权限树结构
     */
    @GetMapping("/tree")
    public Response<List<PermissionTreeNode>> listPermissionTree() {
        if (log.isDebugEnabled()) {
            log.debug("查询权限树");
        }

        try {
            List<PermissionTreeNode> tree = permissionService.listPermissionTree();
            return Response.success(tree);
        } catch (BizException e) {
            log.warn("查询权限树失败: code={}, message={}", e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("查询权限树参数错误: message={}", e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
