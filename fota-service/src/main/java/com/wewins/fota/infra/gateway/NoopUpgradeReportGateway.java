package com.wewins.fota.infra.gateway;

import com.wewins.fota.domain.reporting.model.UpgradeReport;
import com.wewins.fota.domain.reporting.service.UpgradeReportGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 设备上报网关的 Noop 实现。
 *
 * <p>在 MQ 关闭场景下启用，仅记录日志，不执行实际投递。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopUpgradeReportGateway implements UpgradeReportGateway {

    /**
     * 接收上报但不投递。
     *
     * @param report 设备上报模型
     */
    @Override
    public void accept(UpgradeReport report) {
        log.debug("上报事件已接收，后续将接入 MQ pipeline: imei={}, eventType={}",
                report.getImei(), report.getEventType());
    }
}
