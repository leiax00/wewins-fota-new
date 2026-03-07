package com.wewins.fota.domain.reporting.service;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;

/**
 * 设备检查日志网关（领域端口）。
 *
 * <p>用于隔离检查日志通道实现细节（例如 RabbitMQ、HTTP 或 Noop）。</p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
public interface CheckLogGateway {

    /**
     * 接收设备检查日志并投递到下游通道。
     *
     * @param checkLog 设备检查日志领域模型
     */
    void accept(DeviceCheckLog checkLog);
}
