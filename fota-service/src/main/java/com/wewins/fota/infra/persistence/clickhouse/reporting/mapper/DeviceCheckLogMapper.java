package com.wewins.fota.infra.persistence.clickhouse.reporting.mapper;
import com.wewins.fota.database.annotation.ClickHouseMapper;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备检查日志 ClickHouse Mapper（infra 实现）
 */
@Mapper
@ClickHouseMapper
public interface DeviceCheckLogMapper {

    int insert(DeviceCheckLog log);

    int insertBatch(@Param("list") List<DeviceCheckLog> logs);
}
