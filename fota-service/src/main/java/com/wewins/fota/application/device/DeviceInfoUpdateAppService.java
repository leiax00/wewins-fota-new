package com.wewins.fota.application.device;

import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceCache;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInfoUpdateAppService {

    private final DeviceRepository deviceRepository;
    private final DeviceCacheRepository deviceCacheRepository;

    @Transactional(transactionManager = "primaryTransactionManager")
    public void processBatch(List<DeviceInfoUpdateMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        log.info("开始批量处理设备信息更新: count={}", messages.size());

        Map<String, DeviceInfoUpdateMessage> messageMap = messages.stream()
                .collect(Collectors.toMap(
                        DeviceInfoUpdateMessage::getImei,
                        Function.identity(),
                        this::pickLatestMessage
                ));

        List<DeviceInfoUpdateMessage> deduplicated = List.copyOf(messageMap.values());
        deviceRepository.applyCheckUpdates(deduplicated);

        try {
            updateDeviceCacheBatch(deduplicated);
        } catch (Exception e) {
            log.error("更新设备缓存失败: device count={}", deduplicated.size(), e);
        }

        log.info("设备信息批量更新完成: count={}", deduplicated.size());
    }

    private DeviceInfoUpdateMessage pickLatestMessage(DeviceInfoUpdateMessage existing, DeviceInfoUpdateMessage replacement) {
        if (existing == null) {
            return replacement;
        }
        if (replacement == null) {
            return existing;
        }
        if (replacement.getAccessTime() == null) {
            return existing;
        }
        if (existing.getAccessTime() == null) {
            return replacement;
        }
        return replacement.getAccessTime().isAfter(existing.getAccessTime()) ? replacement : existing;
    }

    private void updateDeviceCacheBatch(List<DeviceInfoUpdateMessage> messages) {
        int successCount = 0;
        for (DeviceInfoUpdateMessage message : messages) {
            try {
                DeviceCache deviceCache = getDeviceCache(message.getImei());
                if (deviceCache == null) {
                    continue;
                }
                DeviceVersionParts parts = mergeVersionParts(deviceCache, message);
                deviceCache.setVersionParts(parts);
                deviceCache.setCachedAt(LocalDateTime.now());

                deviceCacheRepository.put(message.getImei(), deviceCache);
                successCount++;
                log.debug("设备缓存已更新: imei={}, deviceId={}", message.getImei(), message.getDeviceId());
            } catch (Exception e) {
                log.warn("更新单个设备缓存失败: imei={}", message.getImei(), e);
            }
        }
        log.info("设备缓存批量更新完成: total={}, success={}", messages.size(), successCount);
    }

    private DeviceCache getDeviceCache(String imei) {
        CacheLookupResult<DeviceCache> lookup = deviceCacheRepository.get(imei);
        if (lookup.hit()) {
            return lookup.value();
        }
        Optional<Device> deviceOp = deviceRepository.findByImei(imei);
        if (deviceOp.isPresent()) {
            Device device = deviceOp.get();
            return DeviceCache.builder()
                    .deviceId(device.getId())
                    .productId(device.getProductId())
                    .versionParts(device.getVersionParts())
                    .tags(device.getTags())
                    .importBatchId(device.getImportBatchId())
                    .build();
        }
        return null;
    }

    private DeviceVersionParts mergeVersionParts(DeviceCache cached, DeviceInfoUpdateMessage message) {
        DeviceVersionParts merged = cached.getVersionParts();
        Map<String, DeviceVersionPart> currentParts = message.getCurrentVersionParts();
        if (currentParts == null || currentParts.isEmpty()) {
            return merged;
        }

        if (merged == null) {
            merged = DeviceVersionParts.builder().build();
        }

        DeviceVersionParts finalMerged = merged;
        currentParts.forEach((partName, snapshot) -> {
            if (snapshot == null) {
                return;
            }
            finalMerged.addPart(partName, snapshot);
        });

        return merged;
    }
}
