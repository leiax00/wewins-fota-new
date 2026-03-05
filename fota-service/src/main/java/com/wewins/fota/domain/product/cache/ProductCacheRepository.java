package com.wewins.fota.domain.product.cache;

import com.wewins.fota.domain.cache.CacheLookupResult;
import com.wewins.fota.domain.product.entity.Product;

import java.util.Optional;

public interface ProductCacheRepository {

    Optional<Product> findById(Long productId);

    CacheLookupResult<Long> getModelLookup(String model);

    void cacheProduct(Product product);

    void cacheProductByModel(String model, Long productId);

    void cacheModelNotFound(String model);

    void evict(Long productId);

    void evictByModel(String model);
}
