package com.wewins.fota.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.system.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
