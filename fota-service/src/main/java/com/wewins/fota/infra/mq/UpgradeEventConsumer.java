package com.wewins.fota.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.reporting.UpgradeEventIngestAppService;
import com.wewins.fota.application.reporting.dto.UpgradeEventMessage;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 升级事件消费者
 * <p>
 * 从 RabbitMQ 接收设备上报事件，批量写入 ClickHouse
 * 使用手动 ACK，失败时拒绝并进入 DLQ
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class UpgradeEventConsumer {

    private final ObjectMapper objectMapper;
    private final UpgradeEventIngestAppService upgradeEventIngestAppService;

    /**
     * 监听升级事件队列
     * <p>
     * 通过 queuesToDeclare 方式自动声明队列
     * 队列名称从配置文件读取：${app.mq.queues.upgrade-events.name}
     * </p>
     *
     * @param payload 消息体（JSON 字符串）
     * @param message RabbitMQ 消息对象
     * @param channel RabbitMQ 通道
     * @throws IOException 消息处理失败时抛出
     */
    @RabbitListener(
            queuesToDeclare = @Queue(
                    name = "${app.mq.queues.upgrade-events.name:fota.upgrade.events}",
                    durable = "${app.mq.queues.upgrade-events.durable:true}"
            )
    )
    public void onUpgradeEvent(String payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            UpgradeEventMessage eventMessage = parseMessage(payload);
            upgradeEventIngestAppService.ingest(eventMessage);

            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.debug("升级事件处理成功: deliveryTag={}, eventCount={}", deliveryTag,
                    eventMessage.getEvents() != null ? eventMessage.getEvents().size() : 0);

        } catch (Exception ex) {
            log.error("升级事件处理失败，拒绝并进入 DLQ: deliveryTag={}", deliveryTag, ex);
            // 拒绝消息，不重新入队，进入 DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 解析消息体
     *
     * @param payload JSON 字符串
     * @return UpgradeEventMessage 对象
     */
    private UpgradeEventMessage parseMessage(String payload) {
        try {
            return objectMapper.readValue(payload, UpgradeEventMessage.class);
        } catch (Exception e) {
            log.error("消息解析失败: payload={}", payload, e);
            throw new RuntimeException("消息解析失败", e);
        }
    }

}
