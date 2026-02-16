package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.policy.PolicyApplicationService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品管理控制器
 * <p>
 * 提供产品 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/admin/product")
@ConditionalOnAppMode("main")
@RequiredArgsConstructor
public class ProductController {

    private final PolicyApplicationService policyApplicationService;

    /**
     * 获取产品列表
     *
     * @return 产品列表
     */
    @GetMapping
    public ApiResponse<List<Product>> listProducts() {
        // TODO: 实现产品列表查询
        return ApiResponse.success(List.of());
    }

    /**
     * 获取产品详情
     *
     * @param id 产品 ID
     * @return 产品详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Product> getProduct(@PathVariable Long id) {
        // TODO: 实现产品详情查询
        return ApiResponse.success(new Product());
    }

    /**
     * 创建产品
     *
     * @param product 产品信息
     * @return 创建的产品
     */
    @PostMapping
    public ApiResponse<Product> createProduct(@RequestBody Product product) {
        // TODO: 实现产品创建
        return ApiResponse.success(product);
    }

    /**
     * 更新产品
     *
     * @param id 产品 ID
     * @param product 产品信息
     * @return 更新后的产品
     */
    @PutMapping("/{id}")
    public ApiResponse<Product> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        // TODO: 实现产品更新
        return ApiResponse.success(product);
    }

    /**
     * 删除产品
     *
     * @param id 产品 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        // TODO: 实现产品删除
        return ApiResponse.success();
    }
}
