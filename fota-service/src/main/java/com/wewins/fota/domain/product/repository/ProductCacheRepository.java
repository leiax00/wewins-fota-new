package com.wewins.fota.domain.product.repository;

import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.product.model.entity.Product;

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
