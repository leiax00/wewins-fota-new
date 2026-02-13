package com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统权限 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
@PrimaryDbMapper
public interface PermissionMapper extends BaseMapperX<Permission> {
}
