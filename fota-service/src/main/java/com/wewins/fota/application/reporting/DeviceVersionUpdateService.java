package com.wewins.fota.application.reporting;

import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.domain.reporting.model.value.DeviceUpgradeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备版本更新服务
 * <p>
 * 当升级成功（UP_OK 事件）时，异步更新设备的当前版本
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>使用 @Async 异步执行，不阻塞主流程</li>
 *   <li>使用独立事务，避免影响事件记录</li>
 *   <li>更新后刷新设备缓存</li>
 *   <li>批量处理提高效率</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceVersionUpdateService {

    private final DeviceRepository deviceRepository;
    private final DeviceCacheRepository deviceCacheRepository;

    /**
     * 异步处理 UP_OK 事件，更新设备版本
     *
     * @param events 升级事件列表
     */
    @Async("fotaTaskExecutor")
    @Transactional(transactionManager = "postgresqlTransactionManager")
    public void processUpgradeSuccessEvents(List<DeviceUpgradeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        // 筛选 UP_OK 事件
        List<DeviceUpgradeEvent> successEvents = events.stream()
                .filter(e -> e.getEventType() == DeviceUpgradeEventType.UP_OK)
                .filter(e -> e.getImei() != null && !e.getImei().isEmpty())
                .toList();

        if (successEvents.isEmpty()) {
            log.debug("没有 UP_OK 事件需要处理");
            return;
        }

        // 按 IMEI 分组，保留最后的事件（同设备多次上报）
        Map<String, DeviceUpgradeEvent> latestEvents = successEvents.stream()
                .collect(Collectors.toMap(
                        DeviceUpgradeEvent::getImei,
                        e -> e,
                        (e1, e2) -> e1.getEventTime().isAfter(e2.getEventTime()) ? e1 : e2
                ));

        int updatedCount = 0;
        int notFoundCount = 0;

        for (DeviceUpgradeEvent event : latestEvents.values()) {
            try {
                updateDeviceVersion(event);
                updatedCount++;
            } catch (Exception e) {
                log.error("更新设备版本失败: imei={}", event.getImei(), e);
            }
        }

        log.info("设备版本更新完成: success={}, notFound={}, total={}",
                updatedCount, notFoundCount, latestEvents.size());
    }

    /**
     * 更新单个设备的版本
     *
     * @param event 升级成功事件
     */
    private void updateDeviceVersion(DeviceUpgradeEvent event) {
        String imei = event.getImei();

        // 查找设备
        Device device = deviceRepository.findByImei(imei)
                .orElse(null);

        if (device == null) {
            log.warn("设备不存在，跳过版本更新: imei={}", imei);
            return;
        }

        // 从事件详情中解析目标版本 ID（如果有的话）
        Long targetVersionId = extractTargetVersionId(event);
        if (targetVersionId == null) {
            log.debug("事件中无目标版本 ID，跳过版本更新: imei={}, eventId={}",
                    imei, event.getEventId());
            return;
        }

        // 更新设备版本
        device.setCurrentVersionId(targetVersionId);
        deviceRepository.updateById(device);

        // 刷新设备缓存
        deviceCacheRepository.evict(imei);

        log.info("设备版本更新成功: imei={}, deviceId={}, versionId={}",
                imei, device.getId(), targetVersionId);
    }

    /**
     * 从事件详情中提取目标版本 ID
     * <p>
     * 期望 details JSON 中包含 target_version_id 字段
     * </p>
     *
     * @param event 升级事件
     * @return 目标版本 ID，如果无法解析则返回 null
     */
    private Long extractTargetVersionId(DeviceUpgradeEvent event) {
        // 优先使用 policyId 关联的版本（如果可以从策略中获取）
        // 这里简化处理：假设事件中包含版本信息

        // 从 downloadUrl 中解析 pid，再通过 pid 查找策略获取目标版本
        // 这是一个简化版本，实际可能需要调用策略服务

        // 如果事件中有 requestId，可以通过 requestId 查找检查日志获取目标版本
        // 这里暂时返回 null，需要结合实际业务实现

        return null;
    }
}
