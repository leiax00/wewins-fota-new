package com.wewins.fota.infra.persistence.clickhouse.reporting.mapper;
import com.wewins.fota.database.annotation.ClickHouseMapper;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备升级事件 ClickHouse Mapper（infra 实现）
 */
@Mapper
@ClickHouseMapper
public interface DeviceUpgradeEventMapper {

    int insert(DeviceUpgradeEvent event);

    int insertBatch(@Param("list") List<DeviceUpgradeEvent> events);
}
