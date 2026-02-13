package com.wewins.fota.domain.reporting.repository;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;

import java.util.List;

/**
 * 升级事件仓储（领域端口）。
 */
public interface UpgradeEventRepository {

    void insertCheckLog(DeviceCheckLog log);

    void insertCheckLogs(List<DeviceCheckLog> logs);

    void insertUpgradeEvent(DeviceUpgradeEvent event);

    void insertUpgradeEvents(List<DeviceUpgradeEvent> events);
}
