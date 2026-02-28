package com.wewins.fota.adapter.assembler;

import com.wewins.fota.application.product.dto.ProductReqDTO;
import com.wewins.fota.application.product.dto.ProductRespDTO;
import com.wewins.fota.domain.product.entity.Product;
import org.springframework.stereotype.Component;

/**
 * 产品 DTO 转换器
 */
@Component
public class ProductAssembler {

    /**
     * 将 ProductReqDTO 转换为 Product 实体
     */
    public Product toProductEntity(ProductReqDTO req) {
        if (req == null) {
            return null;
        }
        return Product.builder()
                .name(req.getName())
                .manufacturer(req.getManufacturer())
                .model(req.getModel())
                .remark(req.getRemark())
                .build();
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
                .createdAt(product.getCreatedAt())
                .createdBy(product.getCreatedBy())
                .updatedAt(product.getUpdatedAt())
                .updatedBy(product.getUpdatedBy())
                .build();
    }
}
