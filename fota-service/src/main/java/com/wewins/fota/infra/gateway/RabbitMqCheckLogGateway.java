package com.wewins.fota.infra.gateway;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
import com.wewins.fota.mq.core.MqMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 设备检查日志网关的 RabbitMQ 实现。
 *
 * <p>将设备检查日志发送到 RabbitMQ 队列，供消费者批量写入 ClickHouse。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMqCheckLogGateway implements CheckLogGateway {

    private final MqMessagePublisher mqMessagePublisher;

    @Value("${app.mq.queues.check-logs.name:fota.check.logs}")
    private String checkLogQueue;

    /**
     * 通过 RabbitMQ 投递检查日志。
     *
     * @param checkLog 设备检查日志领域模型
     */
    @Override
    public void accept(DeviceCheckLog checkLog) {
        if (checkLog == null) {
            log.warn("检查日志为空，跳过投递");
            return;
        }

        try {
            mqMessagePublisher.publishJson(checkLogQueue, checkLog);
            log.debug("检查日志已投递到 MQ: requestId={}, imei={}", checkLog.getRequestId(), checkLog.getImei());
        } catch (Exception e) {
            log.error("检查日志发送失败: requestId={}, imei={}", checkLog.getRequestId(), checkLog.getImei(), e);
            throw new IllegalStateException("检查日志发送失败", e);
        }
    }
}
