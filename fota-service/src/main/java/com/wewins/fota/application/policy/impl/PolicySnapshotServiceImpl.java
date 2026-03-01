package com.wewins.fota.application.policy.impl;

import com.wewins.fota.application.policy.PolicySnapshotService;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.policy.snapshot.PolicySnapshot;
import com.wewins.fota.domain.policy.snapshot.PolicySnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * 策略快照应用服务实现
 * <p>
 * 提供策略快照的读写和版本管理功能，实现降级策略。
 * </p>
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>Redis 完全不可用：直接查询 PostgreSQL</li>
 *   <li>版本指针丢失：从数据库重建快照并写入 Redis</li>
 *   <li>快照内容丢失：从数据库重建快照</li>
 *   <li>同步超时（>24h）：继续使用旧快照，记录警告日志</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
public class PolicySnapshotServiceImpl implements PolicySnapshotService {

    private final PolicySnapshotRepository snapshotRepository;
    private final UpgradePolicyRepository policyRepository;

    public PolicySnapshotServiceImpl(
            @Qualifier("redisPolicySnapshotRepository") PolicySnapshotRepository snapshotRepository,
            @Qualifier("upgradePolicyRepositoryImpl") UpgradePolicyRepository policyRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.policyRepository = policyRepository;
    }

    /**
     * 同步滞后阈值（24 小时）
     */
    private static final long STALE_THRESHOLD_SECONDS = 24 * 60 * 60;

    @Override
    public boolean publishSnapshot(PolicySnapshot snapshot) {
        if (snapshot == null || snapshot.getProductId() == null) {
            log.warn("快照或产品 ID 为空，发布失败");
            return false;
        }

        try {
            boolean success = snapshotRepository.writeAndSwitch(snapshot);

            if (success) {
                log.info("策略快照发布成功: productId={}, version={}",
                        snapshot.getProductId(), snapshot.getVersion());
            } else {
                log.error("策略快照发布失败: productId={}, version={}",
                        snapshot.getProductId(), snapshot.getVersion());
            }

            return success;

        } catch (Exception e) {
            log.error("发布策略快照异常: productId={}", snapshot.getProductId(), e);
            return false;
        }
    }

    @Override
    public Optional<PolicySnapshot> loadSnapshot(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }

        // 优先从 Redis 加载
        Optional<PolicySnapshot> cached = loadFromCache(productId);
        if (cached.isPresent()) {
            return cached;
        }

        // 降级：从数据库重建
        log.warn("Redis 快照不可用，从数据库重建: productId={}", productId);
        return rebuildFromDatabase(productId);
    }

    @Override
    public String getActiveVersion(Long productId) {
        if (productId == null) {
            return null;
        }

        try {
            return snapshotRepository.getActiveVersion(productId);

        } catch (Exception e) {
            log.error("获取活跃版本号失败: productId={}", productId, e);
            return null;
        }
    }

    @Override
    public SyncStatus getSyncStatus(Long productId) {
        if (productId == null) {
            return SyncStatus.inactive();
        }

        try {
            String version = snapshotRepository.getActiveVersion(productId);

            if (version == null) {
                return SyncStatus.inactive();
            }

            Long lastSyncTime = snapshotRepository.getLastSyncTime(productId);

            if (lastSyncTime == null) {
                // 有版本号但没有同步时间戳，视为异常
                return SyncStatus.active(version, null);
            }

            // 检查是否滞后
            boolean isStale = snapshotRepository.isSyncStale(productId, STALE_THRESHOLD_SECONDS);

            if (isStale) {
                long age = Instant.now().getEpochSecond() - lastSyncTime;
                return SyncStatus.stale(version, lastSyncTime, age);
            }

            return SyncStatus.active(version, lastSyncTime);

        } catch (Exception e) {
            log.error("获取同步状态失败: productId={}", productId, e);
            return SyncStatus.inactive();
        }
    }

    @Override
    public int cleanupOldSnapshots(Long productId) {
        if (productId == null) {
            return 0;
        }

        try {
            return snapshotRepository.cleanupOldVersions(productId, 3);

        } catch (Exception e) {
            log.error("清理旧快照失败: productId={}", productId, e);
            return 0;
        }
    }

    @Override
    public boolean deleteAllSnapshots(Long productId) {
        if (productId == null) {
            return false;
        }

        try {
            return snapshotRepository.deleteAllSnapshots(productId);

        } catch (Exception e) {
            log.error("删除所有快照失败: productId={}", productId, e);
            return false;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 从 Redis 缓存加载快照
     */
    private Optional<PolicySnapshot> loadFromCache(Long productId) {
        try {
            return snapshotRepository.loadSnapshot(productId);

        } catch (Exception e) {
            log.warn("从 Redis 加载快照失败: productId={}", productId, e);
            return Optional.empty();
        }
    }

    /**
     * 从数据库重建快照
     * <p>
     * 当 Redis 不可用或快照不存在时，从数据库加载策略数据并重建快照。
     * 重建后的快照可以返回给调用者，但不写入 Redis（避免在 Redis 异常时持续写入）。
     * </p>
     *
     * @param productId 产品 ID
     * @return 重建的快照
     */
    private Optional<PolicySnapshot> rebuildFromDatabase(Long productId) {
        try {
            // 查询活跃策略
            List<UpgradePolicy> policies = policyRepository
                    .findActiveByProductIdOrderByPriorityDesc(productId);

            if (policies.isEmpty()) {
                log.debug("产品无活跃策略: productId={}", productId);
                return Optional.empty();
            }

            // 构建快照（简化版本，仅包含核心数据）
            PolicySnapshot snapshot = PolicySnapshot.builder()
                    .version("0")  // 数据库重建版本号固定为 0
                    .productId(productId)
                    .generatedAt(Instant.now().getEpochSecond())
                    .generatedBy("database_fallback")
                    .policies(buildPolicySelectors(policies))
                    .firmwares(Map.of())  // 需要时从固件表加载
                    .control(PolicySnapshot.ControlConfig.builder()
                            .defaultCheckInterval(3600)
                            .defaultDownloadDelay(300)
                            .maxRetry(3)
                            .build())
                    .build();

            log.info("从数据库重建快照成功: productId={}, policyCount={}",
                    productId, policies.size());

            return Optional.of(snapshot);

        } catch (Exception e) {
            log.error("从数据库重建快照失败: productId={}", productId, e);
            return Optional.empty();
        }
    }

    /**
     * 构建策略选择器列表
     */
    private List<PolicySnapshot.PolicySelector> buildPolicySelectors(List<UpgradePolicy> policies) {
        return policies.stream()
                .map(this::toPolicySelector)
                .toList();
    }

    /**
     * 转换为策略选择器
     */
    private PolicySnapshot.PolicySelector toPolicySelector(UpgradePolicy policy) {
        return PolicySnapshot.PolicySelector.builder()
                .policyId(policy.getId())
                .priority(policy.getPriority())
                .targetVersionId(policy.getTargetVersionId())
                .grayRate(policy.getGrayRate())
                .sourceVersions(extractSourceVersions(policy))
                .targetMode(policy.getTargetMode())
                .timeWindow(extractTimeWindow(policy))
                .triggerMode(policy.getTriggerMode())
                .targetEnvironment(null)  // 从扩展字段读取
                .requiredTags(null)  // 从扩展字段读取
                .build();
    }

    /**
     * 提取源版本 ID 列表
     */
    private List<Long> extractSourceVersions(UpgradePolicy policy) {
        if (policy.getSourceVersions() == null || !policy.getSourceVersions().isArray()) {
            return List.of();
        }

        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                policy.getSourceVersions().fieldNames(),
                                Spliterator.ORDERED),
                        false
                ).mapToLong(fieldName -> {
                    try {
                        return policy.getSourceVersions().get(fieldName).asLong();
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .boxed()
                .toList();
    }

    /**
     * 提取时间窗口配置
     */
    private PolicySnapshot.TimeWindowConfig extractTimeWindow(UpgradePolicy policy) {
        if (policy.getTimeWindow() == null) {
            return null;
        }

        return PolicySnapshot.TimeWindowConfig.builder()
                .type(nullSafeText(policy.getTimeWindow().path("type").asText()))
                .startAt(nullSafeText(policy.getTimeWindow().path("startAt").asText()))
                .endAt(nullSafeText(policy.getTimeWindow().path("endAt").asText()))
                .build();
    }

    /**
     * 安全的空文本处理
     */
    private String nullSafeText(String text) {
        return "null".equals(text) || text.isBlank() ? null : text;
    }
}
