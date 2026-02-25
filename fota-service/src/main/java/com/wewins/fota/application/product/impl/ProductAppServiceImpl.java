package com.wewins.fota.application.product.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.product.ProductAppService;
import com.wewins.fota.application.product.dto.ProductPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产品应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAppServiceImpl implements ProductAppService {

    private final ProductRepository productRepository;

    @Override
    public Page<Product> pageProducts(ProductPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new ProductPageReqDTO();
        }
        reqDTO.validate();

        Page<Product> page = new Page<>(reqDTO.getPage(), reqDTO.getSize());

        if (log.isDebugEnabled()) {
            log.debug("分页查询产品: name={}, manufacturer={}, page={}, size={}",
                    reqDTO.getName(), reqDTO.getManufacturer(), reqDTO.getPage(), reqDTO.getSize());
        }

        return productRepository.pageProducts(page, reqDTO.getName(), reqDTO.getManufacturer());
    }

    @Override
    public Product getById(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return productRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product createProduct(Product product) {
        if (product == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品信息不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("创建产品: name={}, manufacturer={}, model={}",
                    product.getName(), product.getManufacturer(), product.getModel());
        }

        // 校验产品名称唯一性
        validateProductNameUnique(product.getName(), null);

        productRepository.create(product);

        log.info("产品创建成功: productId={}, name={}", product.getId(), product.getName());
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product updateProduct(Product product) {
        if (product == null || product.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新产品: productId={}, name={}, manufacturer={}, model={}",
                    product.getId(), product.getName(), product.getManufacturer(), product.getModel());
        }

        // 检查产品是否存在
        Product existingProduct = getById(product.getId());

        // 校验产品名称唯一性
        validateProductNameUnique(product.getName(), product.getId());

        productRepository.updateById(product);

        log.info("产品更新成功: productId={}, name={}", product.getId(), product.getName());
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProduct(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除产品: productId={}", id);
        }

        // 检查产品是否存在
        getById(id);

        boolean result = productRepository.deleteById(id);
        log.info("产品删除成功: productId={}, result={}", id, result);
        return result;
    }

    /**
     * 校验产品名称唯一性
     *
     * @param name 产品名称
     * @param excludeId 排除的产品 ID
     */
    private void validateProductNameUnique(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品名称不能为空");
        }

        long count = productRepository.countByNameExcludingId(name, excludeId);
        if (count > 0) {
            throw new BizException(ErrorCode.PRODUCT_NAME_EXISTS);
        }
    }
}
