package com.wewins.fota.application.common;

import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关联数据名称解析器。
 * <p>
 * 统一处理跨聚合的名称查询，避免 Controller 层直接依赖多个 Repository。
 * 使用批量查询优化性能，避免 N+1 问题。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceNameResolver {

    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final DeviceImportBatchRepository deviceImportBatchRepository;

    /**
     * 批量查询产品名称。
     *
     * @param productIds 产品 ID 集合
     * @return 产品 ID -> 产品名称的映射
     */
    public Map<Long, String> resolveProductNames(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<Long> ids = List.copyOf(productIds);
            List<Product> products = productRepository.listByIds(ids);

            return products.stream()
                    .collect(Collectors.toMap(Product::getId, Product::getName));
        } catch (Exception e) {
            log.error("批量查询产品名称失败: productIds={}", productIds, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量查询固件版本名称。
     *
     * @param versionIds 固件版本 ID 集合
     * @return 版本 ID -> 版本号的映射
     */
    public Map<Long, String> resolveFirmwareVersionNames(Set<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<Long> ids = List.copyOf(versionIds);
            List<FirmwareVersion> versions = firmwareVersionRepository.listByIds(ids);

            return versions.stream()
                    .collect(Collectors.toMap(FirmwareVersion::getId, FirmwareVersion::getVersion));
        } catch (Exception e) {
            log.error("批量查询固件版本名称失败: versionIds={}", versionIds, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量查询设备导入批次名称。
     *
     * @param batchIds 批次 ID 集合
     * @return 批次 ID -> 批次名称的映射
     */
    public Map<Long, String> resolveImportBatchNames(Set<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<Long> ids = List.copyOf(batchIds);
            List<DeviceImportBatch> batches = deviceImportBatchRepository.listByIds(ids);

            return batches.stream()
                    .collect(Collectors.toMap(DeviceImportBatch::getId, DeviceImportBatch::getBatchName));
        } catch (Exception e) {
            log.error("批量查询设备导入批次名称失败: batchIds={}", batchIds, e);
            return Collections.emptyMap();
        }
    }
}
