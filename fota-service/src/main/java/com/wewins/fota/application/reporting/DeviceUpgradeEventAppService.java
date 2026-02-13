package com.wewins.fota.application.reporting;

import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
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
import java.util.UUID;

/**
 * 设备升级事件应用服务。
 *
 * <p>职责：应用层编排（准备 -> 缓存补全 -> 持久化）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUpgradeEventAppService {

    private final UpgradeEventRepository upgradeEventRepository;
    private final DeviceCacheRepository deviceCacheService;

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
            enrichEventFromCache(prepared);
            upgradeEventRepository.insertUpgradeEvent(prepared);
            log.debug("设备升级事件记录成功: eventId={}, requestId={}, eventType={}, deviceId={}, productId={}",
                    prepared.getEventId(), prepared.getRequestId(), prepared.getEventType(),
                    prepared.getDeviceId(), prepared.getProductId());
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
            for (DeviceUpgradeEvent event : prepared) {
                enrichEventFromCache(event);
            }

            if (!prepared.isEmpty()) {
                upgradeEventRepository.insertUpgradeEvents(prepared);

                long cachedCount = prepared.stream()
                        .filter(e -> e.getDeviceId() != null)
                        .count();
                log.info("设备升级事件批量记录成功: count={}, cached={}", prepared.size(), cachedCount);
            }
        } catch (Exception e) {
            log.error("设备升级事件批量记录失败: count={}", events.size(), e);
            throw e;
        }
    }

    private DeviceCheckLog prepareCheckLog(DeviceCheckLog log) {
        if (log.getRequestId() == null) {
            log.setRequestId(UUID.randomUUID().toString());
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
            event.setEventId(UUID.randomUUID().toString());
        }

        if (event.getRequestId() == null) {
            log.warn("升级事件缺少 request_id，将无法关联到检查日志: imei={}, eventType={}",
                    event.getImei(), event.getEventType());
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

    private void enrichEventFromCache(DeviceUpgradeEvent event) {
        try {
            DeviceCache cache = deviceCacheService.get(event.getImei());
            if (cache != null) {
                event.setDeviceId(cache.getDeviceId());
                event.setProductId(cache.getProductId());
                event.setFirmwareVersion(cache.getFirmwareVersion());
                log.debug("设备缓存命中: imei={}, deviceId={}, productId={}",
                        event.getImei(), cache.getDeviceId(), cache.getProductId());
            } else {
                log.debug("设备缓存未命中: imei={}", event.getImei());
            }
        } catch (Exception e) {
            log.error("从缓存补全设备信息失败: imei={}", event.getImei(), e);
        }
    }
}
