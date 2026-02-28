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
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
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

    @Value("${app.mq.consumer.batch.size:100}")
    private int batchSize;

    @Value("${app.mq.consumer.batch.timeout-ms:1000}")
    private long batchTimeoutMs;

    /**
     * 批量处理升级上报事件
     * <p>
     * 支持单条和批量消息，使用 @RabbitListener 的 containerFactory 配置
     * </p>
     *
     * @param payload 消息体（JSON 字符串或 JSON 数组）
     * @param message RabbitMQ 消息对象
     * @param channel RabbitMQ 通道
     * @param deliveryTag 投递标签
     * @throws IOException 消息处理失败时抛出
     */
    @RabbitListener(
            queues = "${app.mq.queues.upgrade-events.name:fota.upgrade.events}",
            containerFactory = "batchRabbitListenerContainerFactory"
    )
    public void onUpgradeReports(
            Object payload,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        List<UpgradeEventMessage> messages = parseMessages(payload);
        if (messages.isEmpty()) {
            log.warn("解析消息为空，跳过处理: deliveryTag={}", deliveryTag);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            processMessages(messages);
            channel.basicAck(deliveryTag, false);
            log.debug("批量消息处理成功: deliveryTag={}, count={}", deliveryTag, messages.size());

        } catch (Exception ex) {
            log.error("批量消息处理失败，拒绝并进入 DLQ: deliveryTag={}, count={}",
                    deliveryTag, messages.size(), ex);
            // 拒绝消息，不重新入队，进入 DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 单条消息兼容处理
     * <p>
     * 当发送方发送单条消息时（非批量），使用此方法处理
     * </p>
     *
     * @param payload 消息体（JSON 字符串）
     * @param message RabbitMQ 消息对象
     * @param channel RabbitMQ 通道
     * @throws IOException 消息处理失败时抛出
     */
    @RabbitListener(
            queues = "${app.mq.queues.upgrade-events.name:fota.upgrade.events}",
            containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void onUpgradeReport(
            String payload,
            Message message,
            Channel channel) throws IOException {

        UpgradeEventMessage eventMessage = parseMessage(payload);
        if (eventMessage == null) {
            log.warn("解析消息为空，跳过处理");
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        try {
            processMessages(List.of(eventMessage));
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            log.debug("单条消息处理成功: eventIdCount={}",
                    eventMessage.getEvents() != null ? eventMessage.getEvents().size() : 0);

        } catch (Exception ex) {
            log.error("单条消息处理失败，拒绝并进入 DLQ", ex);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
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
     * 解析消息（支持单条和批量）
     *
     * @param payload 消息体
     * @return 消息列表
     */
    private List<UpgradeEventMessage> parseMessages(Object payload) {
        if (payload == null) {
            return List.of();
        }

        // 如果是 List，说明是批量消息
        if (payload instanceof List) {
            List<?> list = (List<?>) payload;
            List<UpgradeEventMessage> result = new ArrayList<>();

            for (Object item : list) {
                if (item instanceof String) {
                    UpgradeEventMessage msg = parseMessage((String) item);
                    if (msg != null) {
                        result.add(msg);
                    }
                } else if (item instanceof byte[]) {
                    try {
                        String json = new String((byte[]) item);
                        UpgradeEventMessage msg = parseMessage(json);
                        if (msg != null) {
                            result.add(msg);
                        }
                    } catch (Exception e) {
                        log.error("解析批量消息项失败", e);
                    }
                }
            }

            return result;
        }

        // 单条消息
        String jsonPayload;
        if (payload instanceof String) {
            jsonPayload = (String) payload;
        } else if (payload instanceof byte[]) {
            jsonPayload = new String((byte[]) payload);
        } else {
            jsonPayload = payload.toString();
        }

        UpgradeEventMessage msg = parseMessage(jsonPayload);
        return msg != null ? List.of(msg) : List.of();
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
