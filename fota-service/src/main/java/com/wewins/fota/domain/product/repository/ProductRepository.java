package com.wewins.fota.domain.product.repository;

import com.wewins.fota.domain.product.entity.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 产品仓储（领域端口）。
 */
public interface ProductRepository {

    Optional<Product> findById(Long id);

    List<Product> findAllActiveOrderByUpdatedAtDesc();

    LocalDateTime findLatestUpdatedAt();
}
