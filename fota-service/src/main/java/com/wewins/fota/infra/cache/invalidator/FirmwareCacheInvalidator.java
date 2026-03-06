package com.wewins.fota.infra.cache.invalidator;

import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionLookupCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareCacheInvalidator {

    private final FirmwareCacheRepository firmwareCacheRepository;
    private final FirmwareVersionLookupCacheRepository firmwareVersionLookupCacheRepository;

    public void invalidateOnFirmwarePublish(Long productId, Long versionId, String version, String internalVersion) {
        firmwareCacheRepository.evict(versionId);
        firmwareVersionLookupCacheRepository.evict(productId, version, internalVersion);
        log.info("固件缓存已失效: productId={}, versionId={}", productId, versionId);
    }
}
