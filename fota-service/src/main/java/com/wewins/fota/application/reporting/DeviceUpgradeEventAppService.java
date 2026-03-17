package com.wewins.fota.application.reporting;

import com.wewins.fota.common.util.IdGenerator;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.domain.reporting.repository.UpgradeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备升级事件应用服务。
 *
 * <p>职责：应用层编排（准备 -> 持久化）。</p>
 * <p>设计原则：通过 request_id 关联 device_check_logs 获取设备信息，无需 Redis 缓存补全。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUpgradeEventAppService {

    private final UpgradeEventRepository upgradeEventRepository;

    public void recordCheckLog(DeviceCheckLog checkLog) {
        if (checkLog == null) {
            return;
        }

        try {
            DeviceCheckLog prepared = prepareCheckLog(checkLog);
            upgradeEventRepository.insertCheckLog(prepared);
            log.debug("设备检查日志记录成功: requestId={}, deviceId={}", prepared.getRequestId(), prepared.getDeviceId());
        } catch (Exception e) {
            log.error("设备检查日志记录失败: deviceId={}, imei={}", checkLog.getDeviceId(), checkLog.getImei(), e);
            throw e;
        }
    }

    public void recordCheckLogs(List<DeviceCheckLog> checkLogs) {
        if (checkLogs == null || checkLogs.isEmpty()) {
            return;
        }

        try {
            List<DeviceCheckLog> prepared = prepareCheckLogs(checkLogs);
            if (!prepared.isEmpty()) {
                upgradeEventRepository.insertCheckLogs(prepared);
                log.info("设备检查日志批量记录成功: count={}", prepared.size());
            }
        } catch (Exception e) {
            log.error("设备检查日志批量记录失败: count={}", checkLogs.size(), e);
            throw e;
        }
    }

    public void recordUpgradeEvent(DeviceUpgradeEvent event) {
        if (event == null) {
            return;
        }

        try {
            DeviceUpgradeEvent prepared = prepareUpgradeEvent(event);
            upgradeEventRepository.insertUpgradeEvent(prepared);
            log.debug("设备升级事件记录成功: eventId={}, requestId={}, eventType={}",
                    prepared.getEventId(), prepared.getRequestId(), prepared.getEventType());
        } catch (Exception e) {
            log.error("设备升级事件记录失败: imei={}, eventType={}", event.getImei(), event.getEventType(), e);
            throw e;
        }
    }

    public void recordUpgradeEvents(List<DeviceUpgradeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        try {
            List<DeviceUpgradeEvent> prepared = prepareUpgradeEvents(events);

            if (!prepared.isEmpty()) {
                upgradeEventRepository.insertUpgradeEvents(prepared);
                log.info("设备升级事件批量记录成功: count={}", prepared.size());
            }
        } catch (Exception e) {
            log.error("设备升级事件批量记录失败: count={}", events.size(), e);
            throw e;
        }
    }

    private DeviceCheckLog prepareCheckLog(DeviceCheckLog log) {
        if (log.getRequestId() == null) {
            log.setRequestId(IdGenerator.simpleUUID());
        }

        if (log.getEventTime() == null) {
            log.setEventTime(LocalDateTime.now(ZoneOffset.UTC));
        }

        return log;
    }

    private List<DeviceCheckLog> prepareCheckLogs(List<DeviceCheckLog> logs) {
        List<DeviceCheckLog> validLogs = new ArrayList<>();
        for (DeviceCheckLog log : logs) {
            if (log != null) {
                validLogs.add(prepareCheckLog(log));
            }
        }
        return validLogs;
    }

    private DeviceUpgradeEvent prepareUpgradeEvent(DeviceUpgradeEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(IdGenerator.simpleUUID());
        }

        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now(ZoneOffset.UTC));
        }

        return event;
    }

    private List<DeviceUpgradeEvent> prepareUpgradeEvents(List<DeviceUpgradeEvent> events) {
        List<DeviceUpgradeEvent> validEvents = new ArrayList<>();
        for (DeviceUpgradeEvent event : events) {
            if (event != null) {
                validEvents.add(prepareUpgradeEvent(event));
            }
        }
        return validEvents;
    }
}
