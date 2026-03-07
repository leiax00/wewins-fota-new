package com.wewins.fota.domain.firmware.repository;

import com.wewins.fota.domain.base.vo.CacheLookupResult;

public interface FirmwareVersionLookupCacheRepository {

    CacheLookupResult<Long> get(Long productId, String version, String internalVersion);

    void put(Long productId, String version, String internalVersion, Long versionId);

    void putNotFound(Long productId, String version, String internalVersion);

    void evict(Long productId, String version, String internalVersion);

}
