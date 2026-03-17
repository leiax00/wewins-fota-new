package com.wewins.fota.infra.persistence.clickhouse.reporting.repository;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.domain.reporting.repository.UpgradeEventRepository;
import com.wewins.fota.infra.persistence.clickhouse.reporting.fallback.FallbackEventStorage;
import com.wewins.fota.infra.persistence.clickhouse.reporting.mapper.DeviceCheckLogMapper;
import com.wewins.fota.infra.persistence.clickhouse.reporting.mapper.DeviceUpgradeEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickHouse 升级事件仓储实现（带本地文件降级）。
 * <p>
 * 当 ClickHouse 写入失败时，自动降级到本地文件存储
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UpgradeEventRepositoryImpl implements UpgradeEventRepository {

    private static final int BATCH_SIZE = 1000;

    private final DeviceCheckLogMapper deviceCheckLogMapper;
    private final DeviceUpgradeEventMapper deviceUpgradeEventMapper;

    private final FallbackEventStorage fallbackEventStorage;

    @Override
    public void insertCheckLog(DeviceCheckLog checkLog) {
        try {
            deviceCheckLogMapper.insert(checkLog);
        } catch (Exception e) {
            log.error("写入 ClickHouse 失败（检查日志），尝试降级存储", e);
            // 检查日志暂不支持降级，记录日志即可
        }
    }

    @Override
    public void insertCheckLogs(List<DeviceCheckLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        List<DeviceCheckLog> failedLogs = new ArrayList<>();

        for (int i = 0; i < logs.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, logs.size());
            List<DeviceCheckLog> batch = logs.subList(i, end);

            try {
                deviceCheckLogMapper.insertBatch(batch);
            } catch (Exception e) {
                log.error("批量写入 ClickHouse 失败（检查日志），batchSize={}", batch.size(), e);
                failedLogs.addAll(batch);
            }
        }

        if (!failedLogs.isEmpty()) {
            log.warn("部分检查日志写入 ClickHouse 失败: count={}", failedLogs.size());
        }
    }

    @Override
    public void insertUpgradeEvent(DeviceUpgradeEvent event) {
        try {
            deviceUpgradeEventMapper.insert(event);
        } catch (Exception e) {
            log.error("写入 ClickHouse 失败（升级事件），尝试降级存储: eventId={}",
                    event.getEventId(), e);
            fallbackToLocalStorage(event);
        }
    }

    @Override
    public void insertUpgradeEvents(List<DeviceUpgradeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        List<DeviceUpgradeEvent> failedEvents = new ArrayList<>();

        for (int i = 0; i < events.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, events.size());
            List<DeviceUpgradeEvent> batch = events.subList(i, end);

            try {
                deviceUpgradeEventMapper.insertBatch(batch);
            } catch (Exception e) {
                log.error("批量写入 ClickHouse 失败（升级事件），batchSize={}", batch.size(), e);
                failedEvents.addAll(batch);
            }
        }

        // 降级存储失败的事件
        if (!failedEvents.isEmpty()) {
            log.warn("部分升级事件写入 ClickHouse 失败，尝试降级存储: count={}", failedEvents.size());
            fallbackToLocalStorage(failedEvents);
        }
    }

    /**
     * 降级到本地文件存储
     *
     * @param event 失败的事件
     */
    private void fallbackToLocalStorage(DeviceUpgradeEvent event) {
        try {
            boolean success = fallbackEventStorage.writeEvent(event);
            if (success) {
                log.info("事件已降级到本地文件: eventId={}", event.getEventId());
            } else {
                log.error("事件降级存储失败: eventId={}", event.getEventId());
            }
        } catch (Exception e) {
            log.error("降级存储异常: eventId={}", event.getEventId(), e);
        }
    }

    /**
     * 批量降级到本地文件存储
     *
     * @param events 失败的事件列表
     */
    private void fallbackToLocalStorage(List<DeviceUpgradeEvent> events) {
        try {
            boolean success = fallbackEventStorage.writeEvents(events);
            if (success) {
                log.info("事件已批量降级到本地文件: count={}", events.size());
            } else {
                log.error("事件批量降级存储失败: count={}", events.size());
            }
        } catch (Exception e) {
            log.error("批量降级存储异常: count={}", events.size(), e);
        }
    }
}
