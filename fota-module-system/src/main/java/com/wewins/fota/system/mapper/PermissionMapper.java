package com.wewins.fota.system.mapper;

import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.system.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统权限 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
public interface PermissionMapper extends BaseMapperX<Permission> {
}
