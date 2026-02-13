package com.wewins.fota.application.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.infra.persistence.mybatis.policy.mapper.UpgradePolicyMapper;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.infra.persistence.mybatis.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Main-side internal config query service.
 */
@Service
@RequiredArgsConstructor
public class InternalConfigQueryService {

    private final UpgradePolicyMapper upgradePolicyMapper;
    private final ProductMapper productMapper;

    public long getConfigVersion() {
        long policyVersion = latestPolicyVersion();
        long productVersion = latestProductVersion();
        return Math.max(policyVersion, productVersion);
    }

    public Map<String, Object> getSnapshot(String snapshotType) {
        return switch (snapshotType) {
            case "policy" -> Map.of(
                    "type", "policy",
                    "version", latestPolicyVersion(),
                    "items", listPolicySnapshotItems()
            );
            case "product" -> Map.of(
                    "type", "product",
                    "version", latestProductVersion(),
                    "items", listProductSnapshotItems()
            );
            case "control" -> Map.of(
                    "type", "control",
                    "version", getConfigVersion(),
                    "config", Map.of(
                            "nextCheckIntervalSeconds", 3600,
                            "degradedCheckIntervalSeconds", 21600,
                            "generatedAt", Instant.now().toString()
                    )
            );
            default -> Map.of(
                    "type", snapshotType,
                    "version", getConfigVersion(),
                    "items", List.of()
            );
        };
    }

    private List<UpgradePolicy> listPolicySnapshotItems() {
        return upgradePolicyMapper.selectList(
                new LambdaQueryWrapper<UpgradePolicy>()
                        .isNull(UpgradePolicy::getDeletedAt)
                        .orderByDesc(UpgradePolicy::getPriority)
                        .orderByDesc(UpgradePolicy::getUpdatedAt)
        );
    }

    private List<Product> listProductSnapshotItems() {
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .isNull(Product::getDeletedAt)
                        .orderByDesc(Product::getUpdatedAt)
        );
    }

    private long latestPolicyVersion() {
        UpgradePolicy latest = upgradePolicyMapper.selectOne(
                new LambdaQueryWrapper<UpgradePolicy>()
                        .isNull(UpgradePolicy::getDeletedAt)
                        .orderByDesc(UpgradePolicy::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return toEpochSeconds(latest == null ? null : latest.getUpdatedAt());
    }

    private long latestProductVersion() {
        Product latest = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .isNull(Product::getDeletedAt)
                        .orderByDesc(Product::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return toEpochSeconds(latest == null ? null : latest.getUpdatedAt());
    }

    private long toEpochSeconds(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }
}
