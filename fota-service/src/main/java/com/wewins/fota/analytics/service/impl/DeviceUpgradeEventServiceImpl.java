package com.wewins.fota.analytics.service.impl;

import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheService;
import com.wewins.fota.analytics.entity.DeviceCheckLog;
import com.wewins.fota.analytics.entity.DeviceUpgradeEvent;
import com.wewins.fota.analytics.mapper.DeviceCheckLogMapper;
import com.wewins.fota.analytics.mapper.DeviceUpgradeEventMapper;
import com.wewins.fota.analytics.service.DeviceUpgradeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 设备升级事件服务实现
 * <p>
 * 提供检查日志和升级事件的批量写入服务
 * 集成 Redis 缓存，简化为"不回填"模式
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUpgradeEventServiceImpl implements DeviceUpgradeEventService {

    private final DeviceCheckLogMapper deviceCheckLogMapper;
    private final DeviceUpgradeEventMapper deviceUpgradeEventMapper;
    private final DeviceCacheService deviceCacheService;

    @Override
    public void recordCheckLog(DeviceCheckLog checkLog) {
        if (checkLog == null) {
            return;
        }

        try {
            // 自动生成 request_id
            if (checkLog.getRequestId() == null) {
                checkLog.setRequestId(UUID.randomUUID().toString());
            }

            // 自动填充 event_time（UTC 时区）
            if (checkLog.getEventTime() == null) {
                checkLog.setEventTime(LocalDateTime.now(ZoneOffset.UTC));
            }

            deviceCheckLogMapper.insert(checkLog);
            log.debug("设备检查日志记录成功: requestId={}, deviceId={}", checkLog.getRequestId(), checkLog.getDeviceId());
        } catch (Exception e) {
            log.error("设备检查日志记录失败: deviceId={}, imei={}", checkLog.getDeviceId(), checkLog.getImei(), e);
            throw e;
        }
    }

    @Override
    public void recordCheckLogs(List<DeviceCheckLog> checkLogs) {
        if (checkLogs == null || checkLogs.isEmpty()) {
            return;
        }

        try {
            List<DeviceCheckLog> validLogs = new ArrayList<>();

            for (DeviceCheckLog checkLog : checkLogs) {
                if (checkLog == null) {
                    continue;
                }

                // 自动生成 request_id
                if (checkLog.getRequestId() == null) {
                    checkLog.setRequestId(UUID.randomUUID().toString());
                }

                // 自动填充 event_time（UTC 时区）
                if (checkLog.getEventTime() == null) {
                    checkLog.setEventTime(LocalDateTime.now(ZoneOffset.UTC));
                }

                validLogs.add(checkLog);
            }

            if (!validLogs.isEmpty()) {
                // 分批插入，避免单次 SQL 过大
                int batchSize = 1000;
                for (int i = 0; i < validLogs.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, validLogs.size());
                    List<DeviceCheckLog> batch = validLogs.subList(i, end);
                    deviceCheckLogMapper.insertBatch(batch);
                }
                log.info("设备检查日志批量记录成功: count={}", validLogs.size());
            }
        } catch (Exception e) {
            log.error("设备检查日志批量记录失败: count={}", checkLogs.size(), e);
            throw e;
        }
    }

    @Override
    public void recordUpgradeEvent(DeviceUpgradeEvent event) {
        if (event == null) {
            return;
        }

        try {
            // 自动生成 event_id
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            // 如果没有 request_id，记录警告但不生成新的（破坏关联性）
            if (event.getRequestId() == null) {
                log.warn("升级事件缺少 request_id，将无法关联到检查日志: imei={}, eventType={}",
                        event.getImei(), event.getEventType());
            }

            // 自动填充 event_time（UTC 时区）
            if (event.getEventTime() == null) {
                event.setEventTime(LocalDateTime.now(ZoneOffset.UTC));
            }

            // 从 Redis 缓存补全设备信息（不回填，保持为空也可以）
            enrichEventFromCache(event);

            deviceUpgradeEventMapper.insert(event);
            log.debug("设备升级事件记录成功: eventId={}, requestId={}, eventType={}, deviceId={}, productId={}",
                    event.getEventId(), event.getRequestId(), event.getEventType(),
                    event.getDeviceId(), event.getProductId());
        } catch (Exception e) {
            log.error("设备升级事件记录失败: imei={}, eventType={}",
                    event.getImei(), event.getEventType(), e);
            throw e;
        }
    }

    @Override
    public void recordUpgradeEvents(List<DeviceUpgradeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        try {
            List<DeviceUpgradeEvent> validEvents = new ArrayList<>();

            for (DeviceUpgradeEvent event : events) {
                if (event == null) {
                    continue;
                }

                // 自动生成 event_id
                if (event.getEventId() == null) {
                    event.setEventId(UUID.randomUUID().toString());
                }

                // 如果没有 request_id，记录警告但不生成新的
                if (event.getRequestId() == null) {
                    log.warn("升级事件缺少 request_id，将无法关联到检查日志: imei={}, eventType={}",
                            event.getImei(), event.getEventType());
                }

                // 自动填充 event_time（UTC 时区）
                if (event.getEventTime() == null) {
                    event.setEventTime(LocalDateTime.now(ZoneOffset.UTC));
                }

                // 从 Redis 缓存补全设备信息（不回填，保持为空也可以）
                enrichEventFromCache(event);

                validEvents.add(event);
            }

            if (!validEvents.isEmpty()) {
                // 分批插入，避免单次 SQL 过大
                int batchSize = 1000;
                for (int i = 0; i < validEvents.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, validEvents.size());
                    List<DeviceUpgradeEvent> batch = validEvents.subList(i, end);
                    deviceUpgradeEventMapper.insertBatch(batch);
                }

                // 统计缓存命中率
                long cachedCount = validEvents.stream()
                        .filter(e -> e.getDeviceId() != null)
                        .count();

                log.info("设备升级事件批量记录成功: count={}, cached={}", validEvents.size(), cachedCount);
            }
        } catch (Exception e) {
            log.error("设备升级事件批量记录失败: count={}", events.size(), e);
            throw e;
        }
    }

    /**
     * 从 Redis 缓存补全设备信息
     * <p>
     * 简化版本：不回填，缓存未命中时保持为空
     * </p>
     *
     * @param event 事件对象
     */
    private void enrichEventFromCache(DeviceUpgradeEvent event) {
        try {
            DeviceCache cache = deviceCacheService.get(event.getImei());
            if (cache != null) {
                // 缓存命中，补全字段
                event.setDeviceId(cache.getDeviceId());
                event.setProductId(cache.getProductId());
                event.setFirmwareVersion(cache.getFirmwareVersion());
                log.debug("设备缓存命中: imei={}, deviceId={}, productId={}",
                        event.getImei(), cache.getDeviceId(), cache.getProductId());
            } else {
                // 缓存未命中，字段保持为 null
                log.debug("设备缓存未命中: imei={}", event.getImei());
            }
        } catch (Exception e) {
            log.error("从缓存补全设备信息失败: imei={}", event.getImei(), e);
            // 不抛异常，允许继续写入
        }
    }
}
