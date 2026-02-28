package com.wewins.fota.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.reporting.DeviceUpgradeEventAppService;
import com.wewins.fota.application.reporting.DeviceVersionUpdateService;
import com.wewins.fota.application.reporting.UpgradeEventDeduplicationService;
import com.wewins.fota.application.reporting.dto.UpgradeEventMessage;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备升级上报批量消费者
 * <p>
 * 从 RabbitMQ 批量接收设备上报事件，处理后写入 ClickHouse
 * </p>
 * <p>
 * 核心功能：
 * <ul>
 *   <li>批量接收和处理消息</li>
 *   <li>基于 event_id 的幂等性去重</li>
 *   <li>失败消息进入 DLQ（死信队列）</li>
 *   <li>UP_OK 事件异步更新设备版本</li>
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
 * @since 2026-02-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class UpgradeReportConsumer {

    private final ObjectMapper objectMapper;
    private final DeviceUpgradeEventAppService deviceUpgradeEventAppService;
    private final UpgradeEventDeduplicationService deduplicationService;
    private final DeviceVersionUpdateService deviceVersionUpdateService;

    /**
     * 批量处理升级上报事件
     * <p>
     * Spring AMQP 批量模式下，第一个参数必须是 List&lt;?&gt;
     * 支持单条消息（List.size() == 1）和批量消息
     * </p>
     *
     * @param messages 批量消息列表
     * @throws IOException 消息处理失败时抛出
     */
    @RabbitListener(
            queues = "${app.mq.queues.upgrade-events.name:fota.upgrade.events}",
            containerFactory = "batchRabbitListenerContainerFactory"
    )
    public void onUpgradeReports(List<Message> messages, Channel channel) throws IOException {
        if (messages.isEmpty()) {
            log.warn("批量消息列表为空，跳过处理");
            return;
        }

        // 获取通道（用于 ACK）
        long deliveryTag = messages.getFirst().getMessageProperties().getDeliveryTag();
        long lastDeliveryTag = messages.getLast().getMessageProperties().getDeliveryTag();

        try {
            // 解析所有消息
            List<UpgradeEventMessage> parsedMessages = new ArrayList<>();
            for (Message message : messages) {
                String payload = new String(message.getBody());
                UpgradeEventMessage msg = parseMessage(payload);
                if (msg != null) {
                    parsedMessages.add(msg);
                }
            }

            if (parsedMessages.isEmpty()) {
                log.warn("所有消息解析失败，ACK 并跳过: deliveryTag={}", deliveryTag);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 处理消息
            processMessages(parsedMessages);

            // 批量 ACK（使用最后一个 deliveryTag，multiple=true）
            channel.basicAck(lastDeliveryTag, true);
            log.debug("批量消息处理成功: count={}", parsedMessages.size());

        } catch (Exception ex) {
            log.error("批量消息处理失败，拒绝并进入 DLQ: count={}", messages.size(), ex);
            // 拒绝消息
            channel.basicNack(lastDeliveryTag, true, false);
        }
    }

    /**
     * 处理消息列表
     * <p>
     * 流程：
     * 1. 提取所有事件
     * 2. 去重过滤
     * 3. 批量写入 ClickHouse
     * 4. 标记为已处理
     * 5. 异步更新设备版本（UP_OK 事件）
     * </p>
     *
     * @param messages 消息列表
     */
    private void processMessages(List<UpgradeEventMessage> messages) {
        // 1. 提取所有事件
        List<DeviceUpgradeEvent> allEvents = extractEvents(messages);
        if (allEvents.isEmpty()) {
            log.debug("消息中没有事件，跳过处理");
            return;
        }

        // 2. 提取事件 ID 并去重
        List<String> eventIds = allEvents.stream()
                .map(DeviceUpgradeEvent::getEventId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        List<String> newEventIds = deduplicationService.filterNewEvents(eventIds);
        if (newEventIds.isEmpty()) {
            log.debug("所有事件都已处理过，跳过: totalEvents={}", allEvents.size());
            return;
        }

        // 3. 过滤出未处理的事件
        List<DeviceUpgradeEvent> newEvents = allEvents.stream()
                .filter(e -> newEventIds.contains(e.getEventId()))
                .collect(Collectors.toList());

        log.info("开始处理新事件: newEvents={}, totalEvents={}", newEvents.size(), allEvents.size());

        // 4. 批量写入 ClickHouse
        deviceUpgradeEventAppService.recordUpgradeEvents(newEvents);

        // 5. 标记为已处理
        deduplicationService.markAsProcessed(newEventIds);

        // 6. 异步更新设备版本（UP_OK 事件）
        deviceVersionUpdateService.processUpgradeSuccessEvents(newEvents);

        log.info("事件处理完成: processed={}", newEvents.size());
    }

    /**
     * 解析单条消息
     *
     * @param payload JSON 字符串
     * @return UpgradeEventMessage 对象
     */
    private UpgradeEventMessage parseMessage(String payload) {
        try {
            return objectMapper.readValue(payload, UpgradeEventMessage.class);
        } catch (Exception e) {
            log.error("消息解析失败: payload={}", payload, e);
            return null;
        }
    }

    /**
     * 从消息列表中提取所有事件
     *
     * @param messages 消息列表
     * @return 事件列表
     */
    private List<DeviceUpgradeEvent> extractEvents(List<UpgradeEventMessage> messages) {
        return messages.stream()
                .filter(m -> m.getEvents() != null && !m.getEvents().isEmpty())
                .flatMap(m -> m.getEvents().stream())
                .collect(Collectors.toList());
    }
}
