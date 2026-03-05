package com.wewins.fota.infra.persistence.mybatis.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.cache.CacheLookupResult;
import com.wewins.fota.domain.product.cache.ProductCacheRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.persistence.mybatis.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ProductRepository 的 MyBatis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    private final ProductCacheRepository productCacheRepository;

    @Override
    public Optional<Product> findById(Long id) {
        Optional<Product> cached = productCacheRepository.findById(id);
        if (cached.isPresent()) {
            log.debug("产品缓存命中: productId={}", id);
            return cached;
        }

        Product product = productMapper.selectById(id);
        if (product != null) {
            productCacheRepository.cacheProduct(product);
            log.debug("产品缓存已写入: productId={}", id);
        }

        return Optional.ofNullable(product);
    }

    @Override
    public List<Product> findAllActiveOrderByUpdatedAtDesc() {
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .isNull(Product::getDeletedAt)
                        .orderByDesc(Product::getUpdatedAt)
        );
    }

    @Override
    public LocalDateTime findLatestUpdatedAt() {
        Product latest = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .isNull(Product::getDeletedAt)
                        .orderByDesc(Product::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return latest == null ? null : latest.getUpdatedAt();
    }

    @Override
    public Page<Product> pageProducts(Page<Product> page, String keyword) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                .isNull(Product::getDeletedAt);

        if (StringUtils.hasText(keyword)) {
            // 关键词同时匹配产品名称、制造商、型号
            queryWrapper.and(wrapper -> wrapper.like(Product::getName, keyword)
                    .or()
                    .like(Product::getManufacturer, keyword)
                    .or()
                    .like(Product::getModel, keyword));
        }

        queryWrapper.orderByDesc(Product::getUpdatedAt);

        return productMapper.selectPage(page, queryWrapper);
    }

    @Override
    public List<Product> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getId, ids)
                        .isNull(Product::getDeletedAt)
        );

        // 保持入参顺序
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Product create(Product product) {
        productMapper.insert(product);
        return product;
    }

    @Override
    public Product updateById(Product product) {
        productMapper.updateById(product);
        return product;
    }

    @Override
    public boolean deleteById(Long id) {
        return productMapper.deleteById(id) > 0;
    }

    @Override
    public long countByNameExcludingId(String name, Long excludeId) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getName, name)
                .isNull(Product::getDeletedAt);

        if (excludeId != null) {
            queryWrapper.ne(Product::getId, excludeId);
        }

        return productMapper.selectCount(queryWrapper);
    }

    @Override
    public Optional<Product> findByModel(String model) {
        if (!StringUtils.hasText(model)) {
            return Optional.empty();
        }

        CacheLookupResult<Long> lookup = productCacheRepository.getModelLookup(model);
        if (lookup.hit()) {
            if (lookup.value() == null) {
                log.debug("产品型号负缓存命中: model={}", model);
                return Optional.empty();
            }

            Optional<Product> cached = productCacheRepository.findById(lookup.value());
            if (cached.isPresent()) {
                log.debug("产品型号索引缓存命中: model={}, productId={}", model, lookup.value());
                return cached;
            }

            log.debug("产品型号索引命中但产品缓存缺失，回源并修复索引: model={}, productId={}", model, lookup.value());
            productCacheRepository.evictByModel(model);
        }

        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getModel, model)
                        .isNull(Product::getDeletedAt)
        );

        if (product != null) {
            productCacheRepository.cacheProduct(product);
            productCacheRepository.cacheProductByModel(model, product.getId());
            log.debug("产品型号索引缓存已写入: model={}, productId={}", model, product.getId());
        } else {
            productCacheRepository.cacheModelNotFound(model);
        }

        return Optional.ofNullable(product);
    }
}
