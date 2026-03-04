package com.wewins.fota.domain.firmware.cache;

public interface FirmwareVersionLookupCacheRepository {

    LookupCacheResult get(Long productId, String version, String internalVersion);

    void put(Long productId, String version, String internalVersion, Long versionId);

    void putNotFound(Long productId, String version, String internalVersion);

    void evict(Long productId, String version, String internalVersion);

    record LookupCacheResult(boolean hit, Long versionId) {
        public static LookupCacheResult miss() {
            return new LookupCacheResult(false, null);
        }

        public static LookupCacheResult hit(Long versionId) {
            return new LookupCacheResult(true, versionId);
        }

        public static LookupCacheResult hitNotFound() {
            return new LookupCacheResult(true, null);
        }
    }
}
