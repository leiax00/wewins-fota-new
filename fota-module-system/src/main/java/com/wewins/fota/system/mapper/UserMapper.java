package com.wewins.fota.system.mapper;

import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.system.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
public interface UserMapper extends BaseMapperX<User> {
}
