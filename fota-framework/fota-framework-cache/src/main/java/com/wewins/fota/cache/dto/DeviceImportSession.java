package com.wewins.fota.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备导入会话缓存对象。
 * <p>
 * 用于两阶段导入流程：
 * <ol>
 *   <li>客户端上传文件或输入文本，服务端解析并创建会话（状态=PARSED）</li>
 *   <li>客户端提交导入请求，携带 sessionId 或 imeiList</li>
 *   <li>服务端消费会话（或使用 imeiList），执行导入操作</li>
 * </ol>
 * </p>
 * <p>
 * 存储位置：Redis（key: fota:device:import:sess:{sessionId}, TTL: 2h）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID（UUID 无横线格式）
     */
    private String sessionId;

    /**
     * 原始文件名（文本输入时为 "文本输入"）
     */
    private String fileName;

    /**
     * 解析后的 IMEI 列表
     */
    private List<String> imeis;

    /**
     * 总数
     */
    private Integer totalCount;

    /**
     * 有效 IMEI 数量
     */
    private Integer validCount;

    /**
     * 无效 IMEI 数量
     */
    private Integer invalidCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 是否来自文件上传
     */
    @Builder.Default
    private boolean fromFile = true;
}
