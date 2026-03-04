package com.wewins.fota.domain.firmware.cache;

import com.wewins.fota.domain.firmware.entity.FirmwareVersion;

import java.util.List;
import java.util.Optional;

public interface FirmwareListCacheRepository {

    Optional<List<FirmwareVersion>> findByProductId(Long productId);

    void cacheFirmwareList(Long productId, List<FirmwareVersion> firmwareList);

    void evict(Long productId);
}
