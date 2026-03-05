package com.wewins.fota.domain.product.cache;

import com.wewins.fota.domain.product.entity.Product;

import java.util.Optional;

public interface ProductCacheRepository {

    record LookupCacheResult(boolean hit, Long productId) {
        public static LookupCacheResult miss() {
            return new LookupCacheResult(false, null);
        }

        public static LookupCacheResult hit(Long productId) {
            return new LookupCacheResult(true, productId);
        }

        public static LookupCacheResult hitNotFound() {
            return new LookupCacheResult(true, null);
        }
    }

    Optional<Product> findById(Long productId);

    LookupCacheResult getModelLookup(String model);

    void cacheProduct(Product product);

    void cacheProductByModel(String model, Long productId);

    void cacheModelNotFound(String model);

    void evict(Long productId);

    void evictByModel(String model);
}
