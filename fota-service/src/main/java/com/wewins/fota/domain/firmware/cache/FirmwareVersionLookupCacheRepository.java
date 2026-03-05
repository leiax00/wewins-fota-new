package com.wewins.fota.domain.firmware.cache;

import com.wewins.fota.domain.cache.CacheLookupResult;

public interface FirmwareVersionLookupCacheRepository {

    CacheLookupResult<Long> get(Long productId, String version, String internalVersion);

    void put(Long productId, String version, String internalVersion, Long versionId);

    void putNotFound(Long productId, String version, String internalVersion);

    void evict(Long productId, String version, String internalVersion);

}
