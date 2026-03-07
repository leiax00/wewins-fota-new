package com.wewins.fota.application.product.query;

import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductNameQueryService {

    private final ProductRepository productRepository;

    public Map<Long, String> resolveProductNames(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Product> products = productRepository.listByIds(List.copyOf(productIds));
            return products.stream().collect(Collectors.toMap(Product::getId, Product::getName));
        } catch (Exception e) {
            log.error("批量查询产品名称失败: productIds={}", productIds, e);
            return Collections.emptyMap();
        }
    }
}
