package com.wewins.fota.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.system.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
