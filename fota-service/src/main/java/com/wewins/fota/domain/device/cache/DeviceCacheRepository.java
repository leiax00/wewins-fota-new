package com.wewins.fota.domain.device.cache;

/**
 * 设备缓存仓储（领域端口）。
 */
public interface DeviceCacheRepository {

    DeviceCache get(String imei);

    void put(String imei, DeviceCache cache);

    void evict(String imei);

}
