package com.wewins.fota.application.product;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.product.dto.ProductPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.cache.event.ChangeType;
import com.wewins.fota.infra.cache.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 产品应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAppService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Page<Product> pageProducts(ProductPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new ProductPageReqDTO();
        }
        reqDTO.validate();

        Page<Product> page = new Page<>(reqDTO.getPage(), reqDTO.getSize());

        if (log.isDebugEnabled()) {
            log.debug("分页查询产品: keyword={}, page={}, size={}",
                    reqDTO.getKeyword(), reqDTO.getPage(), reqDTO.getSize());
        }

        return productRepository.pageProducts(page, reqDTO.getKeyword());
    }

    public Product getById(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return productRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public List<Product> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        if (ids.size() > 200) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "ids 数量不能超过 200");
        }

        if (log.isDebugEnabled()) {
            log.debug("批量查询产品: ids={}", ids);
        }

        return productRepository.listByIds(ids);
    }

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
        eventPublisher.publishEvent(new ProductChangedEvent(
                this,
                product.getId(),
                ChangeType.CREATED,
                null,
                product.getModel()));

        log.info("产品创建成功: productId={}, name={}", product.getId(), product.getName());
        return product;
    }

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
        eventPublisher.publishEvent(new ProductChangedEvent(
                this,
                product.getId(),
                ChangeType.UPDATED,
                existingProduct.getModel(),
                product.getModel()));

        log.info("产品更新成功: productId={}, name={}", product.getId(), product.getName());
        return product;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProduct(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除产品: productId={}", id);
        }

        // 检查产品是否存在
        Product existingProduct = getById(id);

        boolean result = productRepository.deleteById(id);
        eventPublisher.publishEvent(new ProductChangedEvent(
                this,
                id,
                ChangeType.DELETED,
                existingProduct.getModel(),
                null));

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
