package com.wewins.fota.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * 应用配置属性
 * <p>
 * 绑定 application.yml 中的 app 配置前缀
 * 提供强类型访问配置
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 运行模式
     * <p>
     * 可选值：main（主区域）、region（区域部署）、standalone（单区域多实例）
     * </p>
     */
    private String mode = "main";

    /**
     * 节点配置
     * <p>
     * main 模式和 region 模式共用此配置
     * </p>
     */
    @NestedConfigurationProperty
    private Node node = new Node();

    /**
     * 功能开关
     */
    @NestedConfigurationProperty
    private AppFeatures features = new AppFeatures();

    /**
     * 服务注册配置
     */
    @NestedConfigurationProperty
    private ServiceRegistry registry = new ServiceRegistry();

    /**
     * 消息队列配置
     */
    @NestedConfigurationProperty
    private MQ mq = new MQ();

    /**
     * 分区主节点选举配置
     */
    @NestedConfigurationProperty
    private Leader leader = new Leader();

    /**
     * 内部接口鉴权配置
     */
    @NestedConfigurationProperty
    private InternalAuth internalAuth = new InternalAuth();
    /**
     * 区域配置（保留用于向下兼容）
     */
    private Region region = new Region();

    /**
     * 主区域配置（已废弃，使用 node 代替）
     */
    private Main main = new Main();

    /**
     * 节点配置
     */
    @Data
    public static class Node {
        /**
         * 节点编码（如：main, us-east, eu-west）
         */
        private String code;

        /**
         * 节点名称
         */
        private String name;

        /**
         * API 基础 URL
         */
        private String baseUrl;

        /**
         * 时区
         */
        private String timeZone;
    }

    /**
     * 服务注册与发现配置
     */
    @Data
    public static class ServiceRegistry {
        /**
         * 是否启用服务注册
         */
        private boolean enabled = true;

        /**
         * 注册信息 TTL（秒）
         */
        private long ttlSeconds = 90;

        /**
         * 心跳间隔（毫秒）
         */
        private long heartbeatIntervalMs = 20000;

        /**
         * 实例 ID（用于区分同一节点下的不同实例）
         */
        private String instanceId = "1";
    }

    /**
     * 消息队列配置
     */
    @Data
    public static class MQ {
        private boolean enabled = true;
        private Queues queues = new Queues();
    }

    /**
     * 队列配置组
     */
    @Data
    public static class Queues {
        private QueueConfig upgradeEvents = new QueueConfig("fota.upgrade.events");
        private QueueConfig checkLogs = new QueueConfig("fota.device.check.logs");
    }

    /**
     * 队列配置
     */
    @Data
    public static class QueueConfig {
        private String name;
        private boolean durable = true;

        /**
         * 默认构造
         *
         * @param name 队列名称
         */
        public QueueConfig(String name) {
            this.name = name;
        }

        /**
         * 完整构造
         *
         * @param name 队列名称
         * @param durable 是否持久化
         */
        public QueueConfig(String name, boolean durable) {
            this.name = name;
            this.durable = durable;
        }
    }

    /**
     * 分区主节点选举配置
     */
    @Data
    public static class Leader {
        /**
         * 是否启用主节点选举
         */
        private boolean enabled = true;

        /**
         * 锁 TTL（秒）
         */
        private long ttlSeconds = 30;

        /**
         * 续约间隔（毫秒）
         */
        private long renewIntervalMs = 10000;
    }

    /**
     * 内部接口鉴权配置
     */
    @Data
    public static class InternalAuth {
        /**
         * 是否启用内部接口鉴权
         */
        private boolean enabled = true;

        /**
         * 允许的时钟偏差（秒）
         */
        private long skewSeconds = 300;

        /**
         * nonce 过期时间（秒）
         */
        private long nonceTtlSeconds = 300;

    }

    /**
     * 区域配置（用于向下兼容）
     */
    @Data
    public static class Region {
        private String code;
        private String timeZone;
        private Long defaultQuota = 100000L;
        private String apiBaseUrl;
        private String syncEndpoint;
    }

    /**
     * 主区域配置（已废弃）
     */
    @Data
    public static class Main {
        /**
         * 主区域控制面入口
         */
        private String baseUrl;

        /**
         * 分区初始密钥（Bootstrap）
         */
        private String bootstrapSecret;
    }
}
