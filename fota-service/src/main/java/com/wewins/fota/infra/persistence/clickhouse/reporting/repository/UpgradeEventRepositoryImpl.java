package com.wewins.fota.infra.persistence.clickhouse.reporting.repository;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.domain.reporting.repository.UpgradeEventRepository;
import com.wewins.fota.infra.persistence.clickhouse.reporting.mapper.DeviceCheckLogMapper;
import com.wewins.fota.infra.persistence.clickhouse.reporting.mapper.DeviceUpgradeEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ClickHouse 升级事件仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class UpgradeEventRepositoryImpl implements UpgradeEventRepository {

    private static final int BATCH_SIZE = 1000;

    private final DeviceCheckLogMapper deviceCheckLogMapper;
    private final DeviceUpgradeEventMapper deviceUpgradeEventMapper;

    @Override
    public void insertCheckLog(DeviceCheckLog log) {
        deviceCheckLogMapper.insert(log);
    }

    @Override
    public void insertCheckLogs(List<DeviceCheckLog> logs) {
        for (int i = 0; i < logs.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, logs.size());
            deviceCheckLogMapper.insertBatch(logs.subList(i, end));
        }
    }

    @Override
    public void insertUpgradeEvent(DeviceUpgradeEvent event) {
        deviceUpgradeEventMapper.insert(event);
    }

    @Override
    public void insertUpgradeEvents(List<DeviceUpgradeEvent> events) {
        for (int i = 0; i < events.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, events.size());
            deviceUpgradeEventMapper.insertBatch(events.subList(i, end));
        }
    }
}
