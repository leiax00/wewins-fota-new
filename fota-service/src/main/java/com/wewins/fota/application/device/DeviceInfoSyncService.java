package com.wewins.fota.application.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.value.DeviceVersionParts;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInfoSyncService {
    
    private final DeviceRepository deviceRepository;
    private final DeviceCacheRepository deviceCacheRepository;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    
    @Async("fotaTaskExecutor")
    @Transactional(transactionManager = "primaryTransactionManager")
    public void syncDeviceInfo(Device device, UpgradeCheckReqDTO request) {
        if (device == null || request == null) {
            return;
        }
        
        if (!isProductMatch(device, request)) {
            log.warn("设备产品不一致，忽略更新: imei={}, dbProductId={}, reqProduct={}",
                    device.getImei(), device.getProductId(), request.getProduct());
            return;
        }
        
        if (request.getVersion() == null || request.getVersion().isEmpty()) {
            log.debug("设备版本为空，跳过同步: imei={}", device.getImei());
            return;
        }
        
        boolean needsUpdate = false;
        StringBuilder updateReason = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        
        needsUpdate = syncVersion(device, request, updateReason, now);
        
        if (device.getFirstSeenAt() == null) {
            device.setFirstSeenAt(now);
            needsUpdate = true;
            updateReason.append("firstSeenAt initialized; ");
        }
        
        device.setLastSeenAt(now);
        needsUpdate = true;
        updateReason.append("lastSeenAt updated; ");
        
        if (needsUpdate) {
            deviceRepository.updateById(device);
            deviceCacheRepository.evict(device.getImei());
            log.info("设备信息已同步: imei={}, reasons=[{}]", device.getImei(), updateReason);
        }
    }
    
    private boolean isProductMatch(Device device, UpgradeCheckReqDTO request) {
        return productRepository.findByModel(request.getProduct())
                .map(product -> product.getId().equals(device.getProductId()))
                .orElse(false);
    }
    
    private boolean syncVersion(Device device, UpgradeCheckReqDTO request, StringBuilder updateReason, LocalDateTime now) {
        FirmwareVersion firmwareVersion = firmwareVersionRepository
                .findByProductIdAndVersion(device.getProductId(), request.getVersion())
                .stream()
                .findFirst()
                .orElse(null);
        
        if (firmwareVersion == null) {
            log.warn("未找到固件版本: productId={}, version={}", device.getProductId(), request.getVersion());
            return false;
        }
        
        String partName = extractPartName(firmwareVersion);
        Long versionId = firmwareVersion.getId();
        
        DeviceVersionParts versionParts = device.getVersionParts();
        if (versionParts == null) {
            versionParts = DeviceVersionParts.builder().build();
        }
        
        if (!versionParts.matchesPartVersionId(partName, versionId)) {
            versionParts.updatePart(partName, versionId, request.getVersion(), now);
            device.setVersionParts(versionParts);
            updateReason.append(String.format("part[%s] updated to %s (id=%d); ", 
                    partName, request.getVersion(), versionId));
            
            if (device.getInitialVersionParts() == null) {
                device.setInitialVersionParts(DeviceVersionParts.builder()
                        .parts(versionParts.getParts())
                        .primaryPart(versionParts.getPrimaryPart())
                        .build());
                updateReason.append("initialVersionParts initialized; ");
            }
            
            return true;
        }
        
        return false;
    }
    
    private String extractPartName(FirmwareVersion firmwareVersion) {
        JsonNode meta = firmwareVersion.getMeta();
        if (meta != null && meta.has("part")) {
            return meta.get("part").asText();
        }
        return "main";
    }
}
