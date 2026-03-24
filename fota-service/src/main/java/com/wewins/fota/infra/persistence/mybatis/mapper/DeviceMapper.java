package com.wewins.fota.infra.persistence.mybatis.mapper;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DevicePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@PrimaryDbMapper
public interface DeviceMapper extends BaseMapper<DevicePO> {
}
