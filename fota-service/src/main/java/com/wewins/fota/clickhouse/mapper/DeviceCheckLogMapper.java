package com.wewins.fota.clickhouse.mapper;

import com.wewins.fota.clickhouse.entity.DeviceCheckLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备检查日志 ClickHouse Mapper
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Mapper
public interface DeviceCheckLogMapper {

    /**
     * 插入单条检查日志
     *
     * @param log 检查日志
     * @return 影响行数
     */
    int insert(DeviceCheckLog log);

    /**
     * 批量插入检查日志
     *
     * @param logs 检查日志列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<DeviceCheckLog> logs);
}
