package com.wewins.fota.domain.policy.model.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 策略快照领域模型
 * <p>
 * 封装产品的完整升级策略快照数据，用于跨区域配置同步。
 * 快照包含策略列表、固件元数据和控制参数，
 * 可直接用于设备升级检查，无需额外查询。
 * </p>
 *
 * <h3>数据结构</h3>
 * <pre>
 * PolicySnapshot {
 *     // 元数据
 *     version: "42"                    // 快照版本号
 *     productId: "1001"                // 产品ID
 *     generatedAt: 1234567890          // 生成时间戳
 *     generatedBy: "main"              // 生成者标识
 *
 *     // 策略列表（预编译的选择器）
 *     policies: [
 *         {
 *             policyId: 101
 *             priority: 100
 *             targetVersionId: 201
 *             grayRate: 50
 *             sourceVersions: [10, 11, 12]
 *             targetMode: "ALL"
 *             timeWindow: {...}
 *             triggerMode: "BOTH"
 *             targetEnvironment: "prod"
 *         }
 *     ]
 *
 *     // 固件元数据索引
 *     firmwares: {
 *         "201": {
 *             versionId: 201
 *             versionNumber: "v2.0.0"
 *             fileSize: 20000000
 *             fileHash: "sha256:abc..."
 *             urlTemplate: "https://cdn.example.com/pkg.bin"
 *             releaseNotes: {...}
 *         }
 *     }
 *
 *     // 控制参数
 *     control: {
 *         defaultCheckInterval: 3600
 *         defaultDownloadDelay: 300
 *         maxRetry: 3
 *     }
 * }
 * </pre>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>由主区域生成并写入 Redis</li>
 *   <li>区域服务通过版本轮询感知变化</li>
 *   <li>拉取新快照后原子切换版本指针</li>
 *   <li>旧版本快照保留 7 天后自动过期</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Value
@Builder
public class PolicySnapshot {

    /**
     * 快照版本号
     * <p>
     * 单调递增的版本标识，用于快照切换和一致性校验
     * </p>
     */
    private final String version;

    /**
     * 产品 ID
     */
    private final Long productId;

    /**
     * 生成时间戳（Unix 秒）
     */
    private final Long generatedAt;

    /**
     * 生成者标识（区域代码）
     * <p>
     * 通常为 "main"，标识快照来源于主区域
     * </p>
     */
    private final String generatedBy;

    /**
     * 策略列表
     * <p>
     * 预编译的策略选择器，按优先级降序排列
     * </p>
     */
    private final List<PolicySelector> policies;

    /**
     * 固件元数据索引
     * <p>
     * key: 版本 ID（字符串格式）
     * value: 固件元数据
     * </p>
     */
    private final Map<String, FirmwareMetadata> firmwares;

    /**
     * 控制参数
     */
    private final ControlConfig control;

    /**
     * 策略选择器
     * <p>
     * 预编译的策略匹配条件，用于快速判断设备是否匹配策略
     * </p>
     */
    @Value
    @Builder
    public static class PolicySelector {
        Long policyId;
        Integer priority;
        Long targetVersionId;
        Integer grayRate;
        List<Long> sourceVersions;
        String targetMode;
        TimeWindowConfig timeWindow;
        String triggerMode;
        String targetEnvironment;
        JsonNodeConfig requiredTags;
    }

    /**
     * 时间窗口配置
     */
    @Value
    @Builder
    public static class TimeWindowConfig {
        String type;        // RANGE | DAILY
        String startAt;     // ISO8601 UTC 时间戳
        String endAt;       // ISO8601 UTC 时间戳
    }

    /**
     * JSON 节点配置（用于兼容 JsonNode）
     */
    @Value
    @Builder
    public static class JsonNodeConfig {
        String rawJson;     // 原始 JSON 字符串

        @JsonCreator
        public static JsonNodeConfig of(String rawJson) {
            return new JsonNodeConfig(rawJson);
        }

        @JsonProperty("rawJson")
        public String getRawJson() {
            return rawJson;
        }
    }

    /**
     * 固件元数据
     */
    @Value
    @Builder
    public static class FirmwareMetadata {
        Long versionId;
        String versionNumber;
        Long fileSize;
        String fileHash;
        String urlTemplate;
        Map<String, String> releaseNotes;
        String defaultLanguage;
    }

    /**
     * 控制参数
     */
    @Value
    @Builder
    public static class ControlConfig {
        Integer defaultCheckInterval;
        Integer defaultDownloadDelay;
        Integer maxRetry;
    }

    /**
     * 创建当前时间戳的快照
     */
    public static PolicySnapshot create(Long productId, String version,
                                         List<PolicySelector> policies,
                                         Map<String, FirmwareMetadata> firmwares,
                                         ControlConfig control) {
        return PolicySnapshot.builder()
                .version(version)
                .productId(productId)
                .generatedAt(Instant.now().getEpochSecond())
                .generatedBy("main")
                .policies(policies)
                .firmwares(firmwares)
                .control(control)
                .build();
    }

    /**
     * 检查快照是否过期
     *
     * @param maxAgeSeconds 最大有效期（秒）
     * @return true 表示已过期
     */
    public boolean isExpired(long maxAgeSeconds) {
        long age = Instant.now().getEpochSecond() - generatedAt;
        return age > maxAgeSeconds;
    }

    /**
     * 获取固件元数据
     *
     * @param versionId 版本 ID
     * @return 固件元数据，不存在时返回 null
     */
    public FirmwareMetadata getFirmwareMetadata(Long versionId) {
        if (versionId == null) {
            return null;
        }
        return firmwares.get(String.valueOf(versionId));
    }
}
