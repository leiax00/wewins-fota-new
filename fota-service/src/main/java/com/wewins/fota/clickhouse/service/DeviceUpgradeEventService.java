package com.wewins.fota.clickhouse.service;

import com.wewins.fota.clickhouse.entity.DeviceCheckLog;
import com.wewins.fota.clickhouse.entity.DeviceUpgradeEvent;

import java.util.List;

/**
 * 设备升级事件服务接口
 * <p>
 * 提供 ClickHouse 日志记录服务，包括检查日志和升级事件
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public interface DeviceUpgradeEventService {

    /**
     * 记录设备检查日志
     *
     * @param log 检查日志
     */
    void recordCheckLog(DeviceCheckLog log);

    /**
     * 批量记录设备检查日志
     *
     * @param logs 检查日志列表
     */
    void recordCheckLogs(List<DeviceCheckLog> logs);

    /**
     * 记录设备升级事件
     *
     * @param event 升级事件
     */
    void recordUpgradeEvent(DeviceUpgradeEvent event);

    /**
     * 批量记录设备升级事件
     *
     * @param events 升级事件列表
     */
    void recordUpgradeEvents(List<DeviceUpgradeEvent> events);
}
