package com.wewins.fota.infra.persistence.mybatis.product;

import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.infra.persistence.mybatis.product.mapper.ProductMapper;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}
