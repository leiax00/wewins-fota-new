package com.wewins.fota.infra.mq;

import com.wewins.fota.application.reporting.DeviceUpgradeEventAppService;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备检查日志批量消费者
 * <p>
 * 从 RabbitMQ 批量接收设备检查日志，处理后写入 ClickHouse
 * </p>
 * <p>
 * 核心功能：
 * <ul>
 *   <li>批量接收和处理消息</li>
 *   <li>失败消息进入 DLQ（死信队列）</li>
 * </ul>
 * </p>
 * <p>
 * 配置要求：
 * <ul>
 *   <li>spring.rabbitmq.listener.simple.acknowledge-mode=manual</li>
 *   <li>spring.rabbitmq.listener.simple.prefetch=批量大小（建议 100-500）</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class CheckLogConsumer {

    private final MessageConverter messageConverter;
    private final DeviceUpgradeEventAppService deviceUpgradeEventAppService;

    /**
     * 批量处理检查日志
     * <p>
     * Spring AMQP 批量模式下，第一个参数必须是 List&lt;?&gt;
     * 支持单条消息（List.size() == 1）和批量消息
     * </p>
     *
     * @param messages 批量消息列表
     * @param channel  RabbitMQ 通道
     * @throws IOException 消息处理失败时抛出
     */
    @RabbitListener(
            queues = "${app.mq.queues.check-logs.name:fota.check.logs}",
            containerFactory = "checkLogBatchRabbitListenerContainerFactory"
    )
    public void onCheckLogs(List<Message> messages, Channel channel) throws IOException {
        if (messages.isEmpty()) {
            log.warn("批量消息列表为空，跳过处理");
            return;
        }

        long lastDeliveryTag = messages.getLast().getMessageProperties().getDeliveryTag();

        try {
            // 解析所有消息
            List<DeviceCheckLog> checkLogs = parseMessages(messages);
            if (checkLogs.isEmpty()) {
                log.warn("所有消息解析失败，ACK 并跳过: deliveryTag={}", lastDeliveryTag);
                channel.basicAck(lastDeliveryTag, true);
                return;
            }

            // 批量写入 ClickHouse
            deviceUpgradeEventAppService.recordCheckLogs(checkLogs);

            // 批量 ACK（使用最后一个 deliveryTag，multiple=true）
            channel.basicAck(lastDeliveryTag, true);
            log.debug("检查日志批量处理成功: count={}", checkLogs.size());

        } catch (Exception ex) {
            log.error("检查日志批量处理失败，拒绝并进入 DLQ: count={}", messages.size(), ex);
            // 拒绝消息（不重新入队，进入 DLQ）
            channel.basicNack(lastDeliveryTag, true, false);
        }
    }

    /**
     * 解析消息列表
     *
     * @param messages 原始消息列表
     * @return 解析后的检查日志列表
     */
    private List<DeviceCheckLog> parseMessages(List<Message> messages) {
        List<DeviceCheckLog> checkLogs = new ArrayList<>();
        for (Message message : messages) {
            try {
                Object payload = messageConverter.fromMessage(message);
                if (payload instanceof DeviceCheckLog checkLog) {
                    checkLogs.add(checkLog);
                } else {
                    log.warn("消息类型不匹配: expected=DeviceCheckLog, actual={}",
                            payload.getClass().getName());
                }
            } catch (Exception e) {
                log.error("检查日志消息解析失败: payload={}", new String(message.getBody()), e);
            }
        }
        return checkLogs;
    }
}
