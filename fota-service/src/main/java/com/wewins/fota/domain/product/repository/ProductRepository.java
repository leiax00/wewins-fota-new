package com.wewins.fota.domain.product.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /**
     * 分页查询产品列表
     *
     * @param page 分页参数
     * @param name 产品名称（模糊查询，可选）
     * @param manufacturer 制造商（模糊查询，可选）
     * @return 分页结果
     */
    Page<Product> pageProducts(Page<Product> page, String name, String manufacturer);

    /**
     * 创建产品
     *
     * @param product 产品实体
     * @return 创建后的产品
     */
    Product create(Product product);

    /**
     * 更新产品
     *
     * @param product 产品实体
     * @return 更新后的产品
     */
    Product updateById(Product product);

    /**
     * 删除产品（逻辑删除）
     *
     * @param id 产品 ID
     * @return 是否成功
     */
    boolean deleteById(Long id);

    /**
     * 检查产品名称是否存在（排除指定 ID）
     *
     * @param name 产品名称
     * @param excludeId 排除的产品 ID
     * @return 存在的数量
     */
    long countByNameExcludingId(String name, Long excludeId);
}
