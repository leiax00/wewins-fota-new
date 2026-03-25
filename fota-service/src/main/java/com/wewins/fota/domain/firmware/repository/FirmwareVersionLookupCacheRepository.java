package com.wewins.fota.domain.firmware.repository;

import com.wewins.fota.domain.base.vo.CacheLookupResult;

import java.util.List;

public interface FirmwareVersionLookupCacheRepository {

    CacheLookupResult<List<Long>> get(Long productId, String version, String internalVersion);

    void put(Long productId, String version, String internalVersion, List<Long> versionIds);

    void putNotFound(Long productId, String version, String internalVersion);

    void evict(Long productId, String version, String internalVersion);

}
