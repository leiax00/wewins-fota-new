package com.wewins.fota.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 请求工具类
 * <p>
 * 提供客户端 IP 提取和标准化功能
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
public final class HttpUtils {

    private HttpUtils() {
        // 工具类禁止实例化
    }

    /**
     * 提取客户端 IP 地址
     * <p>
     * 优先从 X-Forwarded-For 或 X-Real-IP 头获取
     * </p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP，如果无法获取则返回 null
     */
    public static String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return normalizeIp(xForwardedFor.split(",")[0].trim());
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return normalizeIp(xRealIp.trim());
        }

        return normalizeIp(request.getRemoteAddr());
    }

    /**
     * 标准化 IP 地址
     * <p>
     * 处理以下情况：
     * <ul>
     *   <li>IPv6 localhost 转换为 IPv4 (0:0:0:0:0:0:0:1 或 ::1 -> 127.0.0.1)</li>
     *   <li>移除 IPv6 映射前缀 (::ffff:192.168.1.1 -> 192.168.1.1)</li>
     * </ul>
     * </p>
     *
     * @param ip 原始 IP 地址
     * @return 标准化后的 IP 地址，如果输入为空则返回 null
     */
    public static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        // IPv6 localhost 转换为 IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        // 移除 IPv6 前缀（如 ::ffff:192.168.1.1 -> 192.168.1.1）
        if (ip.startsWith("::ffff:")) {
            return ip.substring(7);
        }
        return ip;
    }
}
