package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.ProductAssembler;
import com.wewins.fota.application.product.ProductAppService;
import com.wewins.fota.application.product.dto.ProductPageReqDTO;
import com.wewins.fota.application.product.dto.ProductReqDTO;
import com.wewins.fota.application.product.dto.ProductRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.product.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

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
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductAppService productAppService;
    private final ProductAssembler productAssembler;

    /**
     * 分页查询产品列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:product:read')")
    public ApiResponse<PageResponse<ProductRespDTO>> listProducts(@ModelAttribute ProductPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new ProductPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询产品: name={}, manufacturer={}, page={}, size={}",
                    reqDTO.getName(), reqDTO.getManufacturer(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<?> pageResult = productAppService.pageProducts(reqDTO);
        List<ProductRespDTO> records = pageResult.getRecords().stream()
                .map(productAssembler::toProductResp)
                .toList();

        PageResponse<ProductRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    /**
     * 获取产品详情
     *
     * @param id 产品 ID
     * @return 产品详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:product:read')")
    public ApiResponse<ProductRespDTO> getProduct(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取产品详情: productId={}", id);
        }

        try {
            Product product = productAppService.getById(id);
            return ApiResponse.success(productAssembler.toProductResp(product));
        } catch (IllegalArgumentException e) {
            log.warn("获取产品详情失败: productId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 创建产品
     *
     * @param reqDTO 产品信息
     * @return 创建的产品
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:product:create')")
    public ApiResponse<ProductRespDTO> createProduct(@RequestBody ProductReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建产品: name={}, manufacturer={}, model={}",
                    reqDTO.getName(), reqDTO.getManufacturer(), reqDTO.getModel());
        }

        try {
            Product product = productAssembler.toProductEntity(reqDTO);
            product.setId(null);
            Product createdProduct = productAppService.createProduct(product);
            log.info("产品创建成功: productId={}, name={}", createdProduct.getId(), createdProduct.getName());
            return ApiResponse.success(productAssembler.toProductResp(createdProduct));
        } catch (BizException e) {
            log.warn("创建产品失败: name={}, errorCode={}, message={}",
                    reqDTO.getName(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建产品参数错误: name={}, message={}", reqDTO.getName(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 更新产品
     *
     * @param id 产品 ID
     * @param reqDTO 产品信息
     * @return 更新后的产品
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:product:update')")
    public ApiResponse<ProductRespDTO> updateProduct(@PathVariable Long id, @RequestBody ProductReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新产品: productId={}", id);
        }

        try {
            Product product = productAssembler.toProductEntity(reqDTO);
            product.setId(id);
            Product updatedProduct = productAppService.updateProduct(product);
            log.info("产品更新成功: productId={}", updatedProduct.getId());
            return ApiResponse.success(productAssembler.toProductResp(updatedProduct));
        } catch (BizException e) {
            log.warn("更新产品失败: productId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新产品参数错误: productId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 删除产品
     *
     * @param id 产品 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:product:delete')")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除产品: productId={}", id);
        }

        try {
            productAppService.getById(id);
            productAppService.deleteProduct(id);
            log.info("产品删除成功: productId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除产品失败: productId={}, errorCode={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除产品参数错误: productId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
