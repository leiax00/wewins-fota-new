package com.wewins.fota.infra.persistence.clickhouse.reporting.mapper;

import com.wewins.fota.database.annotation.ClickHouseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
@ClickHouseMapper
public interface TimelineMapper {

    List<Map<String, Object>> getDeviceTimeline(@Param("imei") String imei,
                                                @Param("days") int days,
                                                @Param("cursor") Long cursor,
                                                @Param("size") int size);
}
