package com.wewins.fota.domain.product.cache;

import com.wewins.fota.domain.product.entity.Product;

import java.util.Optional;

public interface ProductCacheRepository {

    Optional<Product> findById(Long productId);

    Optional<Product> findByModel(String model);

    void cacheProduct(Product product);

    void cacheProductByModel(String model, Long productId);

    void evict(Long productId);

    void evictByModel(String model);
}
