package com.wewins.fota.domain.firmware.repository;

import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;

import java.util.Optional;

public interface FirmwareCacheRepository {

    Optional<FirmwareVersion> findById(Long versionId);

    void cacheFirmware(FirmwareVersion firmware);

    void evict(Long versionId);
}
