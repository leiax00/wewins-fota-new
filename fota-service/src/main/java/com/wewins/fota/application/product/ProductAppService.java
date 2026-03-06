package com.wewins.fota.application.product;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.product.dto.ProductPageReqDTO;
import com.wewins.fota.domain.product.model.entity.Product;

import java.util.List;

/**
 * 产品应用服务接口
 */
public interface ProductAppService {

    /**
     * 分页查询产品列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    Page<Product> pageProducts(ProductPageReqDTO reqDTO);

    /**
     * 根据 ID 获取产品
     *
     * @param id 产品 ID
     * @return 产品实体
     */
    Product getById(Long id);

    /**
     * 批量查询产品列表（按 ID）
     *
     * @param ids 产品 ID 列表
     * @return 产品列表
     */
    List<Product> listByIds(List<Long> ids);

    /**
     * 创建产品
     *
     * @param product 产品实体
     * @return 创建后的产品
     */
    Product createProduct(Product product);

    /**
     * 更新产品
     *
     * @param product 产品实体
     * @return 更新后的产品
     */
    Product updateProduct(Product product);

    /**
     * 删除产品
     *
     * @param id 产品 ID
     * @return 是否成功
     */
    boolean deleteProduct(Long id);
}
