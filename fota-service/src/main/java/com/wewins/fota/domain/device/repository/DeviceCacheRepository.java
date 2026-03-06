package com.wewins.fota.domain.device.repository;

import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.device.model.vo.DeviceCache;

import java.util.List;

/**
 * 设备缓存仓储（领域端口）。
 */
public interface DeviceCacheRepository {

    CacheLookupResult<DeviceCache> get(String imei);

    void put(String imei, DeviceCache cache);

    void putNotFound(String imei);

    void evict(String imei);

    void evictBatch(List<String> imeis);

}
