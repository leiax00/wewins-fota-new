package com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统权限 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
@PrimaryDbMapper
public interface PermissionMapper extends BaseMapperX<Permission> {

    /**
     * 查询用户拥有的菜单权限（MODULE/MENU）
     * <p>
     * 业务约定：如果用户拥有子节点权限，则必定拥有父节点权限
     * 因此只需要查询用户直接拥有的 MODULE/MENU 权限，然后在内存中组织成树
     * </p>
     *
     * @param userId 用户ID
     * @return 菜单权限列表（MODULE/MENU 类型，已排序）
     */
    @Select("""
            SELECT p.*
            FROM sys_permissions p
            JOIN (
                SELECT DISTINCT rp.permission_id
                FROM sys_user_role ur
                JOIN sys_role_permission rp ON rp.role_id = ur.role_id
                WHERE ur.user_id = #{userId}
            ) granted ON granted.permission_id = p.id
            WHERE p.deleted = 0
              AND p.status = 'active'
              AND p.type IN ('MODULE', 'MENU')
            ORDER BY
              CASE WHEN p.parent_id IS NULL THEN 0 ELSE 1 END,
              p.parent_id,
              CASE WHEN p.menu_sort IS NULL THEN 1 ELSE 0 END,
              p.menu_sort,
              p.id
            """)
    List<Permission> findMenuPermissionsByUserId(@Param("userId") Long userId);
}
