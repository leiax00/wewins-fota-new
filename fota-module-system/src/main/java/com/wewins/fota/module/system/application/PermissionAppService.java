package com.wewins.fota.module.system.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.application.dto.PermissionTreeNode;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.repository.rbac.PermissionRepository;
import com.wewins.fota.module.system.domain.repository.rbac.RolePermissionRepository;
import com.wewins.fota.module.system.dto.PermissionPageReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PermissionAppService  {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MenuAppService menuAppService;

    public PermissionAppService(PermissionRepository permissionRepository,
                                 RolePermissionRepository rolePermissionRepository,
                                 MenuAppService menuAppService) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.menuAppService = menuAppService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Permission createPermission(Permission permission) {
        if (log.isDebugEnabled()) {
            log.debug("创建权限: code={}, parentId={}", permission.getCode(), permission.getParentId());
        }

        validatePermissionCodeUnique(permission.getCode(), null);
        validateParent(permission.getParentId(), null);

        permissionRepository.create(permission);

        // 清除所有用户菜单缓存
        menuAppService.evictAllUserMenusCache();

        log.info("权限创建成功: permissionId={}, code={}", permission.getId(), permission.getCode());
        return permission;
    }

    @Transactional(rollbackFor = Exception.class)
    public Permission updatePermission(Permission permission) {
        if (permission.getId() == null) {
            throw new IllegalArgumentException("权限ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新权限: permissionId={}, code={}, parentId={}", permission.getId(), permission.getCode(), permission.getParentId());
        }

        validatePermissionCodeUnique(permission.getCode(), permission.getId());
        validateParent(permission.getParentId(), permission.getId());

        permissionRepository.updateById(permission);

        // 清除所有用户菜单缓存
        menuAppService.evictAllUserMenusCache();

        log.info("权限更新成功: permissionId={}", permission.getId());
        return permission;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deletePermission(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("权限ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除权限: permissionId={}", permissionId);
        }

        if (rolePermissionRepository.countByPermissionId(permissionId) > 0) {
            throw new IllegalStateException("存在角色关联该权限，无法删除");
        }

        if (permissionRepository.countByParentId(permissionId) > 0) {
            throw new IllegalStateException("存在子权限，无法删除");
        }

        boolean result = permissionRepository.deleteById(permissionId);

        // 清除所有用户菜单缓存
        menuAppService.evictAllUserMenusCache();

        log.info("权限删除成功: permissionId={}, result={}", permissionId, result);
        return result;
    }

    public Permission getPermissionById(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("权限ID不能为空");
        }
        return permissionRepository.findById(permissionId);
    }

    public Page<Permission> pagePermissions(PermissionPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new PermissionPageReqDTO();
        }
        reqDTO.validate();
        return permissionRepository.page(reqDTO);
    }

    public List<PermissionTreeNode> listPermissionTree() {
        List<Permission> permissions = permissionRepository.findAllOrderByParentIdAndId();

        Map<Long, PermissionTreeNode> nodeMap = new HashMap<>();
        List<PermissionTreeNode> roots = new ArrayList<>();

        for (Permission permission : permissions) {
            PermissionTreeNode node = PermissionTreeNode.builder().permission(permission).children(new ArrayList<>()).build();
            nodeMap.put(permission.getId(), node);
        }

        for (Permission permission : permissions) {
            PermissionTreeNode node = nodeMap.get(permission.getId());
            Long parentId = permission.getParentId();

            if (parentId == null) {
                roots.add(node);
            } else {
                PermissionTreeNode parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    log.warn("权限的父节点不存在，当作根节点处理: permissionId={}, parentId={}", permission.getId(), parentId);
                    roots.add(node);
                }
            }
        }

        log.debug("权限树构建完成: 总权限数={}, 根节点数={}", permissions.size(), roots.size());
        return roots;
    }

    private void validatePermissionCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("权限编码不能为空");
        }

        if (permissionRepository.countByCodeExcludingId(code, excludeId) > 0) {
            throw new IllegalArgumentException("权限编码已存在: " + code);
        }
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }

        if (parentId.equals(selfId)) {
            throw new IllegalArgumentException("父权限不能是自身");
        }

        if (selfId != null) {
            List<Permission> allPermissions = permissionRepository.findAllOrderByParentIdAndId();
            Map<Long, Permission> permissionMap = allPermissions.stream().collect(Collectors.toMap(Permission::getId, p -> p));

            Permission parent = permissionMap.get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父权限不存在");
            }

            Set<Long> visited = new HashSet<>();
            Long currentParentId = parentId;
            int maxDepth = 100;
            int depth = 0;

            while (currentParentId != null && depth < maxDepth) {
                if (currentParentId.equals(selfId)) {
                    throw new IllegalArgumentException("检测到权限循环引用，无法设置该父权限");
                }

                if (!visited.add(currentParentId)) {
                    log.warn("检测到权限数据异常：父节点链存在重复节点: parentId={}", currentParentId);
                    break;
                }

                Permission current = permissionMap.get(currentParentId);
                currentParentId = (current != null) ? current.getParentId() : null;
                depth++;
            }

            if (depth >= maxDepth) {
                log.warn("权限层级超过最大深度限制: maxDepth={}", maxDepth);
            }
        } else {
            Permission parent = permissionRepository.findById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父权限不存在");
            }
        }
    }
}
