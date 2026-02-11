package com.wewins.fota.infra.config;

import lombok.Data;

/**
 * 集群部署配置属性
 * <p>
 * 支持同区域多实例集群部署（负载均衡场景）
 * </p>
 * <p>
 * 注意：
 * <ul>
 *   <li>负载均衡场景下，所有实例平等提供服务，无需 Leader 选举</li>
 *   <li>配额控制等并发操作通过 Redis 原子操作实现，无需复杂的分布式锁</li>
 * </ul>
 * </p>
 * <p>
 * 此类作为 AppProperties.cluster 的内部类使用，
 * 不需要单独的 @ConfigurationProperties 注解
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
public class ClusterProperties {

    /**
     * 是否启用集群模式
     * <p>
     * 负载均衡场景下，标记当前是集群部署即可
     * </p>
     */
    private boolean enabled = false;

    /**
     * 分布式锁配置
     * <p>
     * 注意：推荐使用 Redis 原子操作（increment/decrement）替代分布式锁
     * </p>
     */
    private Lock lock = new Lock();

    @Data
    public static class Lock {
        /**
         * 是否启用分布式锁
         */
        private boolean enabled = false;

        /**
         * 锁前缀
         */
        private String prefix = "fota:lock:";

        /**
         * 锁租约时间（秒）
         */
        private int leaseTime = 30;
    }
}
