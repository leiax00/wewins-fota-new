package com.wewins.fota.infra.persistence.mybatis.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.infra.persistence.mybatis.product.mapper.ProductMapper;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ProductRepository 的 MyBatis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(productMapper.selectById(id));
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
    public Page<Product> pageProducts(Page<Product> page, String name, String manufacturer) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                .isNull(Product::getDeletedAt);

        if (StringUtils.hasText(name)) {
            queryWrapper.like(Product::getName, name);
        }

        if (StringUtils.hasText(manufacturer)) {
            queryWrapper.like(Product::getManufacturer, manufacturer);
        }

        queryWrapper.orderByDesc(Product::getUpdatedAt);

        return productMapper.selectPage(page, queryWrapper);
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
}
