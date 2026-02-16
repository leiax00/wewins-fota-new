package com.wewins.fota.module.system.infra.persistence.mybatis.mapper.user;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.module.system.domain.entity.user.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
@PrimaryDbMapper
public interface UserMapper extends BaseMapperX<User> {
}
