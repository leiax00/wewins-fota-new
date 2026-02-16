package com.wewins.fota.infra.persistence.mybatis.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.infra.persistence.mybatis.product.mapper.ProductMapper;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ProductRepository 的 MyBatis 实现。
 */
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
}
