package com.wewins.fota.infra.config;

import lombok.Data;

/**
 * 集群部署配置属性
 * <p>
 * 支持同区域多实例集群部署
 * </p>
 * <p>
 * 注意：此类作为 AppProperties.cluster 的内部类使用，
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
     */
    private boolean enabled = false;

    /**
     * 分布式锁配置
     */
    private Lock lock = new Lock();

    /**
     * Leader 选举配置
     */
    private Leader leader = new Leader();

    @Data
    public static class Lock {
        /**
         * 锁前缀
         */
        private String prefix = "fota:lock:";

        /**
         * 锁租约时间（秒）
         */
        private int leaseTime = 30;
    }

    @Data
    public static class Leader {
        /**
         * 是否启用 Leader 选举
         */
        private boolean enabled = false;

        /**
         * 续约间隔（秒）
         */
        private int renewInterval = 10;
    }
}
