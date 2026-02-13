package com.wewins.fota.domain.firmware.repository;

import com.wewins.fota.domain.firmware.entity.FirmwareVersion;

import java.util.Optional;

/**
 * 固件版本仓储（领域端口）。
 */
public interface FirmwareVersionRepository {

    Optional<FirmwareVersion> findById(Long id);
}
