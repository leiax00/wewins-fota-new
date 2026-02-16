package com.wewins.fota.domain.device.cache;

/**
 * 设备缓存仓储（领域端口）。
 */
public interface DeviceCacheRepository {

    DeviceCache get(String imei);

    void put(String imei, DeviceCache cache);

    DeviceCache loadAndCache(String imei);

    void evict(String imei);

    void warmUp(String[] imeis);

    void evictBatch(String[] imeis);

    void evictByProduct(Long productId);

    String[] getDeviceKeys(String[] imeis);
}
