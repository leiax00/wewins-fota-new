package com.wewins.fota.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.database.wrapper.QueryWrapperX;
import com.wewins.fota.system.entity.Permission;
import com.wewins.fota.system.entity.RolePermission;
import com.wewins.fota.system.mapper.PermissionMapper;
import com.wewins.fota.system.mapper.RolePermissionMapper;
import com.wewins.fota.system.service.IPermissionService;
import com.wewins.fota.system.service.dto.PermissionTreeNode;
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

/**
 * 权限服务实现
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Slf4j
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements IPermissionService {

    private final RolePermissionMapper rolePermissionMapper;

    public PermissionServiceImpl(RolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission createPermission(Permission permission) {
        if (log.isDebugEnabled()) {
            log.debug("创建权限: code={}, parentId={}", permission.getCode(), permission.getParentId());
        }

        validatePermissionCodeUnique(permission.getCode(), null);
        validateParent(permission.getParentId(), null);

        save(permission);

        log.info("权限创建成功: permissionId={}, code={}", permission.getId(), permission.getCode());
        return permission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Permission updatePermission(Permission permission) {
        if (permission.getId() == null) {
            throw new IllegalArgumentException("权限ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新权限: permissionId={}, code={}, parentId={}",
                    permission.getId(), permission.getCode(), permission.getParentId());
        }

        validatePermissionCodeUnique(permission.getCode(), permission.getId());
        validateParent(permission.getParentId(), permission.getId());

        updateById(permission);

        log.info("权限更新成功: permissionId={}", permission.getId());
        return permission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePermission(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("权限ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除权限: permissionId={}", permissionId);
        }

        // 检查是否有角色关联该权限
        Long refCount = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getPermissionId, permissionId));

        if (refCount != null && refCount > 0) {
            throw new IllegalStateException("存在角色关联该权限，无法删除");
        }

        // 检查是否有子权限
        Long childCount = baseMapper.selectCount(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getParentId, permissionId));

        if (childCount != null && childCount > 0) {
            throw new IllegalStateException("存在子权限，无法删除");
        }

        boolean result = removeById(permissionId);
        log.info("权限删除成功: permissionId={}, result={}", permissionId, result);
        return result;
    }

    @Override
    public Permission getPermissionById(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("权限ID不能为空");
        }
        return getById(permissionId);
    }

    @Override
    public List<Permission> listPermissions(String keyword, String type, String status) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Permission::getCode, keyword)
                    .or()
                    .like(Permission::getName, keyword)
                    .or()
                    .like(Permission::getPath, keyword));
        }

        if (type != null && !type.isBlank()) {
            wrapper.eq(Permission::getType, type);
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Permission::getStatus, status);
        }

        wrapper.orderByAsc(Permission::getParentId, Permission::getId);
        return list(wrapper);
    }

    @Override
    public Page<Permission> pagePermissions(BaseRequestVo param) {
        if (param == null) {
            param = new BaseRequestVo();
        }
        param.validate();

        QueryWrapperX<Permission> wrapper = new QueryWrapperX<>(Permission.class)
                .applyFiltersIfPresent(param.getFilters())
                .applySortingIfPresent(param.getSortingFields());

        return page(new Page<>(param.getPage(), param.getSize()), wrapper);
    }

    @Override
    public List<PermissionTreeNode> listPermissionTree() {
        // 查询所有权限
        List<Permission> permissions = list(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getParentId)
                .orderByAsc(Permission::getId));

        // 构建节点映射
        Map<Long, PermissionTreeNode> nodeMap = new HashMap<>();
        List<PermissionTreeNode> roots = new ArrayList<>();

        // 创建所有节点
        for (Permission permission : permissions) {
            PermissionTreeNode node = PermissionTreeNode.builder()
                    .permission(permission)
                    .children(new ArrayList<>())
                    .build();
            nodeMap.put(permission.getId(), node);
        }

        // 构建树形结构
        for (Permission permission : permissions) {
            PermissionTreeNode node = nodeMap.get(permission.getId());
            Long parentId = permission.getParentId();

            if (parentId == null) {
                // 根节点
                roots.add(node);
            } else {
                // 子节点
                PermissionTreeNode parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // 父节点不存在，当作根节点处理
                    log.warn("权限的父节点不存在，当作根节点处理: permissionId={}, parentId={}",
                            permission.getId(), parentId);
                    roots.add(node);
                }
            }
        }

        log.debug("权限树构建完成: 总权限数={}, 根节点数={}", permissions.size(), roots.size());
        return roots;
    }

    /**
     * 验证权限编码唯一性
     *
     * @param code 权限编码
     * @param excludeId 排除的权限ID
     */
    private void validatePermissionCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("权限编码不能为空");
        }

        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<Permission>()
                .eq(Permission::getCode, code);

        if (excludeId != null) {
            wrapper.ne(Permission::getId, excludeId);
        }

        Long count = baseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("权限编码已存在: " + code);
        }
    }

    /**
     * 验证父权限
     * <p>
     * 验证父权限存在性，并检测循环引用（包括间接循环）
     * </p>
     *
     * @param parentId 父权限ID
     * @param selfId 当前权限ID
     */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }

        // 不能将自身设为父权限
        if (selfId != null && parentId.equals(selfId)) {
            throw new IllegalArgumentException("父权限不能是自身");
        }

        // 检测循环引用：沿着父节点链向上追溯，避免形成环
        if (selfId != null) {
            // 一次性查询所有权限，构建 ID -> Permission 映射，减少数据库查询次数
            List<Permission> allPermissions = list(new LambdaQueryWrapper<>());
            Map<Long, Permission> permissionMap = allPermissions.stream()
                    .collect(Collectors.toMap(Permission::getId, p -> p));

            // 验证父权限是否存在
            Permission parent = permissionMap.get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父权限不存在");
            }

            // 在内存中检测循环引用
            Set<Long> visited = new HashSet<>();
            Long currentParentId = parentId;
            int maxDepth = 100; // 防止异常数据导致无限循环
            int depth = 0;

            while (currentParentId != null && depth < maxDepth) {
                // 如果在祖先链中找到了自己，说明会形成环
                if (currentParentId.equals(selfId)) {
                    throw new IllegalArgumentException("检测到权限循环引用，无法设置该父权限");
                }

                // 记录已访问的节点
                if (!visited.add(currentParentId)) {
                    // 数据异常：同一节点被访问两次
                    log.warn("检测到权限数据异常：父节点链存在重复节点: parentId={}", currentParentId);
                    break;
                }

                // 继续向上追溯（从内存中的映射获取）
                Permission current = permissionMap.get(currentParentId);
                currentParentId = (current != null) ? current.getParentId() : null;
                depth++;
            }

            if (depth >= maxDepth) {
                log.warn("权限层级超过最大深度限制: maxDepth={}", maxDepth);
            }
        } else {
            // 如果没有 selfId（新建权限），只需验证父权限是否存在
            Permission parent = getById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父权限不存在");
            }
        }
    }
}
