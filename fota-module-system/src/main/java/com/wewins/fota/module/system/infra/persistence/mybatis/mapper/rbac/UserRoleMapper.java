package com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.module.system.domain.entity.rbac.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
@PrimaryDbMapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
