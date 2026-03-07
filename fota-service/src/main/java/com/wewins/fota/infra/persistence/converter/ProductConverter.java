package com.wewins.fota.infra.persistence.converter;

import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.infra.persistence.mybatis.po.ProductPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        builder = @Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductConverter {

    Product toDomain(ProductPO po);

    List<Product> toDomainList(List<ProductPO> poList);

    ProductPO toPo(Product domain);
}
