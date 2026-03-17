package com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.module.system.domain.entity.rbac.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
@PrimaryDbMapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
