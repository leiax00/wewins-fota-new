package com.wewins.fota.application.device;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceCache;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                        (existing, replacement) -> existing
                ));

        List<Device> devices = deviceRepository.findByImeis(new ArrayList<>(messageMap.keySet()));
        Map<String, Device> deviceMap = devices.stream()
                .collect(Collectors.toMap(Device::getImei, Function.identity()));

        List<Device> toUpdate = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (DeviceInfoUpdateMessage message : messageMap.values()) {
            Device device = deviceMap.get(message.getImei());
            if (device == null) {
                log.warn("设备不存在，跳过更新: imei={}", message.getImei());
                continue;
            }

            updateDevice(device, message, now);
            toUpdate.add(device);
        }

        if (!toUpdate.isEmpty()) {
            deviceRepository.updateBatch(toUpdate);

            try {
                updateDeviceCacheBatch(toUpdate);
            } catch (Exception e) {
                log.error("更新设备缓存失败: device count={}", toUpdate.size(), e);
            }

            log.info("设备信息批量更新完成: count={}", toUpdate.size());
        }
    }

    private void updateDevice(Device device, DeviceInfoUpdateMessage message, LocalDateTime now) {
        StringBuilder reasons = new StringBuilder();

        LocalDateTime time = message.getAccessTime() != null ? message.getAccessTime() : now;
        String partName = message.getPartName() != null ? message.getPartName() : "main";
        DeviceVersionParts internalParts = device.getInitialVersionParts();
        DeviceVersionParts versionParts = device.getVersionParts();
        Long newVersionId = message.getNewVersionId();

        if (Boolean.TRUE.equals(message.getIsFirstOnline())) {
            if (device.getFirstSeenAt() == null) {
                device.setFirstSeenAt(time);
                reasons.append("firstSeenAt initialized; ");
            }

            if (newVersionId != null) {
                if (internalParts == null) {
                    internalParts = DeviceVersionParts.builder().build();
                    internalParts.updatePart(partName, newVersionId, time);
                } else {
                    if (!internalParts.hasVersion(partName)) {
                        internalParts.updatePart(partName, newVersionId, time);
                    }
                }
                device.setInitialVersionParts(internalParts);
                reasons.append("initialVersionParts initialized; ");
            }
        }

        if (newVersionId != null) {
            if (versionParts == null) {
                versionParts = DeviceVersionParts.builder().build();
            }
            versionParts.updatePart(partName, newVersionId, time);
            device.setVersionParts(versionParts);
            reasons.append("versionParts updated; ");
        }

        device.setLastSeenAt(time);
        reasons.append("lastSeenAt updated; ");

        device.setStatus("ACTIVE");

        log.debug("设备信息更新: imei={}, reasons=[{}]", device.getImei(), reasons);
    }

    private boolean isVersionMatch(Device device, DeviceInfoUpdateMessage message) {
        DeviceVersionParts parts = device.getVersionParts();
        if (parts == null) {
            return false;
        }

        String partName = message.getPartName() != null ? message.getPartName() : "main";
        return parts.matchesPartVersionId(partName, message.getNewVersionId());
    }

    /**
     * 批量更新设备缓存
     * <p>
     * 将更新后的设备信息直接写入缓存，而不是删除缓存。
     * 这样下次 check 时可以直接从缓存读取，避免数据库查询。
     * </p>
     *
     * @param devices 已更新的设备列表
     */
    private void updateDeviceCacheBatch(List<Device> devices) {
        int successCount = 0;
        for (Device device : devices) {
            try {
                DeviceCache cache = DeviceCache.builder()
                        .deviceId(device.getId())
                        .productId(device.getProductId())
                        .versionParts(device.getVersionParts())
                        .tags(device.getTags())
                        .importBatchId(device.getImportBatchId())
                        .firstSeenAt(device.getFirstSeenAt())
                        .build();
                deviceCacheRepository.put(device.getImei(), cache);
                successCount++;
                log.debug("设备缓存已更新: imei={}, deviceId={}", device.getImei(), device.getId());
            } catch (Exception e) {
                log.warn("更新单个设备缓存失败: imei={}", device.getImei(), e);
            }
        }
        log.info("设备缓存批量更新完成: total={}, success={}", devices.size(), successCount);
    }
}
