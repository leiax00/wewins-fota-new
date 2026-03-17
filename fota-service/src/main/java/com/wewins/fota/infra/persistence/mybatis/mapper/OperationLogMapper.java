package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.domain.audit.model.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@PrimaryDbMapper
public interface OperationLogMapper extends BaseMapperX<OperationLog> {
}
