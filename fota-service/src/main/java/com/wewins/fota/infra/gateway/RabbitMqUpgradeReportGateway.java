package com.wewins.fota.infra.gateway;

import com.wewins.fota.application.reporting.dto.UpgradeEventMessage;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.domain.reporting.model.value.DeviceUpgradeEventType;
import com.wewins.fota.domain.reporting.model.UpgradeReport;
import com.wewins.fota.domain.reporting.service.UpgradeReportGateway;
import com.wewins.fota.mq.core.MqMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

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
     * @param report 设备上报模型
     */
    @Override
    public void accept(UpgradeReport report) {
        try {
            UpgradeEventMessage message = UpgradeEventMessage.builder()
                    .events(List.of(buildEvent(report)))
                    .build();

            mqMessagePublisher.publishJson(upgradeEventQueue, message);
        } catch (Exception e) {
            log.error("设备上报消息发送失败: imei={}, eventType={}", report.getImei(), report.getEventType(), e);
            throw new IllegalStateException("设备上报消息发送失败", e);
        }
    }

    private DeviceUpgradeEvent buildEvent(UpgradeReport report) {
        return DeviceUpgradeEvent.builder()
                .imei(report.getImei())
                .eventType(DeviceUpgradeEventType.fromDbValue(report.getEventType()))
                .downloadUrl(report.getDownloadUrl())
                .details(report.getExt())
                .clientIp(report.getClientIp())
                .build();
    }
}
