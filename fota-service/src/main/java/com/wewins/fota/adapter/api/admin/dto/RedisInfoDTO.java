package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Redis 服务器信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisInfoDTO {

    /** Redis 版本 */
    private String version;

    /** 运行模式 (standalone/cluster/sentinel) */
    private String mode;

    /** 已连接客户端数 */
    private Long connectedClients;

    /** 内存使用量 (字节) */
    private Long usedMemory;

    /** 内存峰值 (字节) */
    private Long usedMemoryPeak;

    /** 分配的内存总量 (字节) */
    private Long totalSystemMemory;

    /** 内存使用率 (%) */
    private Double memoryUsagePercent;

    /** Key 总数 */
    private Long totalKeys;

    /** 过期 Key 数量 */
    private Long expiredKeys;

    /** 驱逐 Key 数量 */
    private Long evictedKeys;

    /** 键空间命中次数 */
    private Long keyspaceHits;

    /** 键空间未命中次数 */
    private Long keyspaceMisses;

    /** 命中率 (%) */
    private Double hitRate;

    /** 总处理命令数 */
    private Long totalCommandsProcessed;

    /** 每秒处理命令数 (instantaneous) */
    private Long instantaneousOpsPerSec;

    /** 运行时间 (秒) */
    private Long uptimeInSeconds;

    /** 最后保存 RDB 时间 (Unix 时间戳) */
    private Long rdbLastSaveTime;

    /** RDB 上次保存状态 */
    private String rdbLastStatus;

    /** AOF 是否启用 */
    private Boolean aofEnabled;

    /** 数据库大小信息 (db0, db1, ...) */
    private Map<String, Long> dbSizes;

    /** 原始 INFO 命令输出 */
    private Map<String, String> rawInfo;
}
