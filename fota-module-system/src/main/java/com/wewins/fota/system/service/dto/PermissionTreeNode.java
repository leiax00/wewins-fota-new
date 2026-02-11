package com.wewins.fota.system.service.dto;

import com.wewins.fota.system.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点 DTO
 * <p>
 * 用于构建权限的树形结构，支持父子权限的层级展示
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionTreeNode {

    /**
     * 权限实体
     */
    private Permission permission;

    /**
     * 子权限列表
     */
    @Builder.Default
    private List<PermissionTreeNode> children = new ArrayList<>();
}
