package com.wewins.fota.infra.gateway;

import com.wewins.fota.common.util.IdGenerator;
import com.wewins.fota.domain.reporting.model.entity.UpgradeReport;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.domain.reporting.service.UpgradeReportGateway;
import com.wewins.fota.mq.core.MqMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 设备上报网关的 RabbitMQ 实现。
 *
 * <p>将设备上报转换为升级事件消息，并发送到升级事件队列。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMqUpgradeReportGateway implements UpgradeReportGateway {

    private final MqMessagePublisher mqMessagePublisher;

    @Value("${app.mq.queues.upgrade-events.name:fota.upgrade.events}")
    private String upgradeEventQueue;

    /**
     * 通过 RabbitMQ 投递上报事件。
     *
     * @param report 设备上报领域模型
     */
    @Override
    public void accept(UpgradeReport report) {
        if (report == null) {
            log.warn("上报数据为空，跳过投递");
            return;
        }

        try {
            DeviceUpgradeEvent payload = buildEvent(report);
            mqMessagePublisher.publishJson(upgradeEventQueue, payload);
        } catch (Exception e) {
            log.error("设备上报消息发送失败: imei={}, requestId={}, event={}",
                    report.getImei(), report.getRequestId(), report.getEvent(), e);
            throw new IllegalStateException("设备上报消息发送失败", e);
        }
    }

    private DeviceUpgradeEvent buildEvent(UpgradeReport report) {
        return DeviceUpgradeEvent.builder()
                .eventId(IdGenerator.uuid())
                .imei(report.getImei())
                .requestId(report.getRequestId())
                .eventType(report.getEvent())
                .details(report.getDetailsJson())
                .clientIp(report.getClientIp())
                .region(report.getRegion())
                .build();
    }
}
