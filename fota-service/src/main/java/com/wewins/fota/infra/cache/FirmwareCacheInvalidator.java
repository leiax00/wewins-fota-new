package com.wewins.fota.infra.cache;

import com.wewins.fota.domain.firmware.cache.FirmwareCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareCacheInvalidator {

    private final FirmwareCacheRepository firmwareCacheRepository;

    public void invalidateOnFirmwarePublish(Long productId, Long versionId) {
        firmwareCacheRepository.evict(versionId);
        log.info("固件缓存已失效: productId={}, versionId={}", productId, versionId);
    }
}
