package com.wewins.fota.application.config;

import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
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

    private final UpgradePolicyRepository upgradePolicyRepository;
    private final ProductRepository productRepository;

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
        return upgradePolicyRepository.findAllActiveOrderByPriorityAndUpdatedAt();
    }

    private List<Product> listProductSnapshotItems() {
        return productRepository.findAllActiveOrderByUpdatedAtDesc();
    }

    private long latestPolicyVersion() {
        return toEpochSeconds(upgradePolicyRepository.findLatestUpdatedAt());
    }

    private long latestProductVersion() {
        return toEpochSeconds(productRepository.findLatestUpdatedAt());
    }

    private long toEpochSeconds(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }
}
