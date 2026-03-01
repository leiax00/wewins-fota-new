package com.wewins.fota.domain.reporting.service;

import com.wewins.fota.domain.reporting.model.UpgradeReport;

/**
 * 设备上报网关（领域端口）。
 *
 * <p>用于隔离上报通道实现细节（例如 RabbitMQ、HTTP 或 Noop）。</p>
 */
public interface UpgradeReportGateway {

    /**
     * 接收设备升级上报并投递到下游通道。
     *
     * @param report 设备上报领域模型
     */
    void accept(UpgradeReport report);
}
