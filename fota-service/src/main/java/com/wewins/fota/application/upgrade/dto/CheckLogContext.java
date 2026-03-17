package com.wewins.fota.application.upgrade.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 检查日志上下文（HTTP 请求元信息）
 * <p>
 * 用于从 Controller 层传递 HTTP 请求元信息到 Service 层，
 * 包含客户端 IP、User-Agent 和区域标识。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Data
@Builder
public class CheckLogContext {

    /**
     * 客户端 IP 地址
     * <p>
     * 优先从 X-Forwarded-For 或 X-Real-IP 头获取
     * </p>
     */
    private String clientIp;

    /**
     * 用户代理
     * <p>
     * 设备上报的 User-Agent 信息
     * </p>
     */
    private String userAgent;

    /**
     * 区域标识
     * <p>
     * 当前服务节点所属区域（main、region-cn 等）
     * </p>
     */
    private String region;
}
