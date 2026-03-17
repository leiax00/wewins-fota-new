package com.wewins.fota.adapter.assembler;

import com.wewins.fota.common.util.TimeConstants;
import com.wewins.fota.application.product.dto.ProductReqDTO;
import com.wewins.fota.application.product.dto.ProductRespDTO;
import com.wewins.fota.domain.product.model.entity.Product;
import org.springframework.stereotype.Component;

/**
 * 产品 DTO 转换器
 */
@Component
public class ProductAssembler {

    private static final int DEFAULT_CHECK_PERIOD_SECONDS = 6 * TimeConstants.SECONDS_PER_HOUR;

    /**
     * 将 ProductReqDTO 转换为 Product 实体
     */
    public Product toProductEntity(ProductReqDTO req) {
        if (req == null) {
            return null;
        }
        Product.ProductBuilder builder = Product.builder()
                .name(req.getName())
                .manufacturer(req.getManufacturer())
                .model(req.getModel())
                .remark(req.getRemark())
                .checkPeriodSeconds(req.getCheckPeriodSeconds() != null
                        ? req.getCheckPeriodSeconds()
                        : DEFAULT_CHECK_PERIOD_SECONDS);
        return builder.build();
    }

    /**
     * 将 Product 实体转换为 ProductRespDTO
     */
    public ProductRespDTO toProductResp(Product product) {
        if (product == null) {
            return null;
        }
        return ProductRespDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .manufacturer(product.getManufacturer())
                .model(product.getModel())
                .remark(product.getRemark())
                .checkPeriodSeconds(product.getCheckPeriodSeconds())
                .createdAt(product.getCreatedAt())
                .createdBy(product.getCreatedBy())
                .updatedAt(product.getUpdatedAt())
                .updatedBy(product.getUpdatedBy())
                .build();
    }
}
