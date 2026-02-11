package com.wewins.fota.analytics.mapper;

import com.wewins.fota.analytics.entity.DeviceUpgradeEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备升级事件 ClickHouse Mapper
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Mapper
public interface DeviceUpgradeEventMapper {

    /**
     * 插入单条升级事件
     *
     * @param event 升级事件
     * @return 影响行数
     */
    int insert(DeviceUpgradeEvent event);

    /**
     * 批量插入升级事件
     *
     * @param events 升级事件列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<DeviceUpgradeEvent> events);
}
