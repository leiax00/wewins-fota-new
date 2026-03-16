package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Key 详情 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisKeyDetailDTO {

    /** Key 名称 */
    private String key;

    /** Key 类型 (string, hash, list, set, zset, stream, none) */
    private String type;

    /** TTL (秒), -1 表示永不过期, -2 表示已过期或不存在 */
    private Long ttl;

    /** 内存占用大小 (字节) */
    private Long memoryUsage;

    /** 编码类型 (raw, embstr, int, hashtable, ziplist, etc.) */
    private String encoding;

    /** 值内容 (JSON 序列化后的字符串) */
    private Object value;

    /** 值长度/大小 (string 长度, list/set/hash 元素数量) */
    private Long length;

    /** 错误信息 (如果获取失败) */
    private String error;
}
