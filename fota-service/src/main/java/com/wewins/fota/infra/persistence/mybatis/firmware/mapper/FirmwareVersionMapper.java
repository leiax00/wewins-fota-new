package com.wewins.fota.infra.persistence.mybatis.firmware.mapper;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固件版本 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 BaseMapper，提供 CRUD 操作
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Mapper
@PrimaryDbMapper
public interface FirmwareVersionMapper extends BaseMapper<FirmwareVersion> {
    // MyBatis-Plus BaseMapper 已提供常用 CRUD 方法
    // 如需自定义查询，在此添加方法并使用 @Select 注解或创建 XML 文件
}
