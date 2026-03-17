package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Redis Key 列表查询结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyListDTO {

    /** Key 列表 */
    private List<KeyItem> keys;

    /** 总数 */
    private Long total;

    /** 当前页码 */
    private Integer page;

    /** 每页大小 */
    private Integer pageSize;

    /** 扫描使用的 cursor (用于下一次分页) */
    private String cursor;

    /**
     * Key 项信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyItem {

        /** Key 名称 */
        private String key;

        /** Key 类型 (string, hash, list, set, zset, stream, none) */
        private String type;

        /** TTL (秒), -1 表示永不过期, -2 表示已过期或不存在 */
        private Long ttl;

        /** 内存占用大小 (字节), 可能不精确 */
        private Long memoryUsage;
    }
}
