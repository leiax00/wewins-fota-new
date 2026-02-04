package com.wewins.fota.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 *
 * <p>用于验证服务是否正常启动，提供基本的健康检查端点。
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/actuator")
public class HealthController {

    /**
     * 基础健康检查端点
     *
     * @return 健康状态信息
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        return health;
    }

    /**
     * 服务信息端点
     *
     * @return 服务信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "wewins-fota-new");
        info.put("version", "0.1.0-SNAPSHOT");
        info.put("description", "千万级 IoT 设备固件 OTA 管理平台");
        return info;
    }
}
