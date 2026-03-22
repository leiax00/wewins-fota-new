package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.product.repository.ProductCacheRepository;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.persistence.converter.ProductConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.ProductMapper;
import com.wewins.fota.infra.persistence.mybatis.po.ProductPO;
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

    private final ProductConverter productConverter;

    @Override
    public Optional<Product> findById(Long id) {
        Optional<Product> cached = productCacheRepository.findById(id);
        if (cached.isPresent()) {
            log.debug("产品缓存命中: productId={}", id);
            return cached;
        }

        ProductPO productPo = productMapper.selectById(id);
        Product product = productConverter.toDomain(productPo);
        if (product != null) {
            productCacheRepository.cacheProduct(product);
            log.debug("产品缓存已写入: productId={}", id);
        }

        return Optional.ofNullable(product);
    }

    @Override
    public List<Product> findAllActiveOrderByUpdatedAtDesc() {
        List<ProductPO> productPos = productMapper.selectList(
                new LambdaQueryWrapper<ProductPO>()
                        .eq(ProductPO::getDeleted, 0)
                        .orderByDesc(ProductPO::getUpdatedAt)
        );
        return productConverter.toDomainList(productPos);
    }

    @Override
    public LocalDateTime findLatestUpdatedAt() {
        ProductPO latest = productMapper.selectOne(
                new LambdaQueryWrapper<ProductPO>()
                        .eq(ProductPO::getDeleted, 0)
                        .orderByDesc(ProductPO::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return latest == null ? null : latest.getUpdatedAt();
    }

    @Override
    public Page<Product> pageProducts(Page<Product> page, String keyword) {
        LambdaQueryWrapper<ProductPO> queryWrapper = new LambdaQueryWrapper<ProductPO>()
                .eq(ProductPO::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            // 关键词同时匹配产品名称、制造商、型号
            queryWrapper.and(wrapper -> wrapper.like(ProductPO::getName, keyword)
                    .or()
                    .like(ProductPO::getManufacturer, keyword)
                    .or()
                    .like(ProductPO::getModel, keyword));
        }

        queryWrapper.orderByDesc(ProductPO::getUpdatedAt);

        Page<ProductPO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<ProductPO> queried = productMapper.selectPage(poPage, queryWrapper);
        Page<Product> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(productConverter.toDomainList(queried.getRecords()));
        return result;
    }

    @Override
    public List<Product> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<ProductPO> products = productMapper.selectList(
                new LambdaQueryWrapper<ProductPO>()
                        .in(ProductPO::getId, ids)
                        .eq(ProductPO::getDeleted, 0)
        );

        // 保持入参顺序
        Map<Long, Product> productMap = productConverter.toDomainList(products).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Product create(Product product) {
        ProductPO po = productConverter.toPo(product);
        productMapper.insert(po);
        return productConverter.toDomain(po);
    }

    @Override
    public Product updateById(Product product) {
        ProductPO po = productConverter.toPo(product);
        productMapper.updateById(po);
        return productConverter.toDomain(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return productMapper.deleteById(id) > 0;
    }

    @Override
    public long countByNameExcludingId(String name, Long excludeId) {
        LambdaQueryWrapper<ProductPO> queryWrapper = new LambdaQueryWrapper<ProductPO>()
                .eq(ProductPO::getName, name)
                .eq(ProductPO::getDeleted, 0);

        if (excludeId != null) {
            queryWrapper.ne(ProductPO::getId, excludeId);
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

        ProductPO productPo = productMapper.selectOne(
                new LambdaQueryWrapper<ProductPO>()
                        .eq(ProductPO::getModel, model)
                        .eq(ProductPO::getDeleted, 0)
        );
        Product product = productConverter.toDomain(productPo);

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
