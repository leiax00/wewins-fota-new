package com.wewins.fota.infra.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SSE 日志广播器
 * <p>
 * 负责：
 * <ul>
 *   <li>管理日志监控页面的 SSE 连接</li>
 *   <li>保存最近 N 条日志作为历史缓冲</li>
 *   <li>为新连接先回放历史日志，再推送实时日志</li>
 *   <li>异步广播日志，避免阻塞 logback 线程</li>
 * </ul>
 */
@Slf4j
@Component
public class SseLogBroadcaster {

    /**
     * 历史日志缓冲区大小
     */
    private static final int HISTORY_CAPACITY = 1000;

    /**
     * 广播队列最大容量（防止内存积压）
     */
    private static final int BROADCAST_QUEUE_CAPACITY = 10000;

    /**
     * SSE 超时时间（0 表示永不超时）
     */
    private static final long SSE_TIMEOUT_MS = 0L;

    /**
     * 心跳间隔（毫秒）
     */
    private static final long HEARTBEAT_INTERVAL_MS = 30000L;

    /**
     * 连接超时时间（毫秒）
     */
    private static final long CONNECTION_TIMEOUT_MS = 60000L;

    private final ReentrantLock historyLock = new ReentrantLock();
    private final LogEvent[] ringBuffer = new LogEvent[HISTORY_CAPACITY];
    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<LogEvent> broadcastQueue = new ConcurrentLinkedQueue<>() {
        @Override
        public boolean offer(LogEvent event) {
            // 限制队列大小，防止内存积压
            if (size() >= BROADCAST_QUEUE_CAPACITY) {
                // 队列满时，移除最旧的元素
                poll();
            }
            return super.offer(event);
        }
    };

    private int writeIndex;
    private int bufferSize;

    @PreDestroy
    public void destroy() {
        clients.values().forEach(client -> {
            try {
                client.emitter.complete();
            } catch (Exception e) {
                log.debug("关闭 SSE 连接时出错", e);
            }
        });
        clients.clear();
        broadcastQueue.clear();
        log.info("SseLogBroadcaster 已销毁");
    }

    /**
     * 创建新的 SSE 连接
     */
    public SseEmitter connect() {
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ClientConnection client = new ClientConnection(emitter);

        emitter.onCompletion(() -> removeClient(clientId));
        emitter.onTimeout(() -> removeClient(clientId));
        emitter.onError(ex -> removeClient(clientId));

        clients.put(clientId, client);

        try {
            // 发送历史日志
            List<LogEvent> history = getHistory();
            for (LogEvent event : history) {
                sendEvent(emitter, event);
            }

            // 发送连接就绪事件
            sendReadyEvent(emitter);

            // 标记为就绪，开始接收实时日志
            client.ready.set(true);

            // 发送在连接期间缓存的事件
            flushPendingEvents(clientId, client);

            log.debug("SSE 日志客户端已连接, clientId={}, 当前连接数={}", clientId, clients.size());
            return emitter;
        } catch (IOException ex) {
            removeClient(clientId);
            throw new IllegalStateException("创建 SSE 日志流失败", ex);
        }
    }

    /**
     * 广播日志事件到所有已连接的客户端（异步，不阻塞 logback 线程）
     */
    public void broadcast(LogEvent event) {
        if (event == null) {
            return;
        }

        // 添加到历史缓冲区
        addToHistory(event);

        // 将事件放入广播队列，由异步线程处理
        broadcastQueue.offer(event);
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return clients.size();
    }

    /**
     * 定时处理广播队列（由 Spring 调度）
     */
    @Scheduled(fixedDelay = 50)
    public void processBroadcastQueue() {
        LogEvent event;
        // 每次最多处理 100 条，避免占用 CPU 过久
        int batchSize = 100;
        int count = 0;
        while ((event = broadcastQueue.poll()) != null && count < batchSize) {
            doBroadcast(event);
            count++;
        }
    }

    /**
     * 发送心跳（由 Spring 调度）
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        clients.forEach((clientId, client) -> {
            // 检查超时（基于上次活动时间）
            if (now - client.lastActivityTime > CONNECTION_TIMEOUT_MS) {
                toRemove.add(clientId);
                return;
            }

            try {
                // 发送注释行作为心跳
                client.emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException ex) {
                toRemove.add(clientId);
            }
        });

        // 清理超时连接
        toRemove.forEach(this::removeClient);
    }

    /**
     * 执行实际的广播操作
     */
    private void doBroadcast(LogEvent event) {
        clients.forEach((clientId, client) -> {
            if (!client.ready.get()) {
                // 客户端未就绪，缓存事件
                client.pendingEvents.offer(event);
                return;
            }

            try {
                sendEvent(client.emitter, event);
                client.lastActivityTime = System.currentTimeMillis();
            } catch (IOException ex) {
                log.debug("推送 SSE 日志事件失败, 关闭连接 clientId={}", clientId, ex);
                removeClient(clientId);
            }
        });
    }

    /**
     * 添加事件到历史缓冲区
     */
    private void addToHistory(LogEvent event) {
        historyLock.lock();
        try {
            ringBuffer[writeIndex] = event;
            writeIndex = (writeIndex + 1) % HISTORY_CAPACITY;
            if (bufferSize < HISTORY_CAPACITY) {
                bufferSize++;
            }
        } finally {
            historyLock.unlock();
        }
    }

    /**
     * 获取历史日志快照
     */
    private List<LogEvent> getHistory() {
        historyLock.lock();
        try {
            List<LogEvent> snapshot = new ArrayList<>(bufferSize);
            // 如果缓冲区满了，从 writeIndex 开始读取（最旧的记录）
            // 如果缓冲区未满，从索引 0 开始读取
            int startIndex = bufferSize == HISTORY_CAPACITY ? writeIndex : 0;

            for (int i = 0; i < bufferSize; i++) {
                int index = (startIndex + i) % HISTORY_CAPACITY;
                LogEvent event = ringBuffer[index];
                if (event != null) {
                    snapshot.add(event);
                }
            }
            return snapshot;
        } finally {
            historyLock.unlock();
        }
    }

    /**
     * 发送缓存的待处理事件
     */
    private void flushPendingEvents(String clientId, ClientConnection client) throws IOException {
        LogEvent pendingEvent;
        while ((pendingEvent = client.pendingEvents.poll()) != null) {
            sendEvent(client.emitter, pendingEvent);
        }

        if (!clients.containsKey(clientId)) {
            throw new IOException("SSE 客户端已断开");
        }
    }

    /**
     * 移除客户端连接
     */
    private void removeClient(String clientId) {
        ClientConnection removed = clients.remove(clientId);
        if (removed == null) {
            return;
        }

        try {
            removed.emitter.complete();
        } catch (Exception e) {
            log.debug("关闭 SSE 客户端时出错, clientId={}", clientId, e);
        }

        removed.pendingEvents.clear();
        log.debug("SSE 日志客户端已断开, clientId={}, 当前连接数={}", clientId, clients.size());
    }

    /**
     * 发送单个日志事件
     */
    private void sendEvent(SseEmitter emitter, LogEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .name("log")
                .data(event, null));
    }

    /**
     * 发送连接就绪事件
     */
    private void sendReadyEvent(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event()
                .name("ready")
                .data("{\"status\":\"connected\",\"message\":\"日志流已连接\"}")
                .id("0"));
    }

    /**
     * 客户端连接封装
     */
    private static final class ClientConnection {

        private final SseEmitter emitter;
        private final AtomicBoolean ready = new AtomicBoolean(false);
        private final ConcurrentLinkedQueue<LogEvent> pendingEvents = new ConcurrentLinkedQueue<>();
        private volatile long lastActivityTime = System.currentTimeMillis();

        private ClientConnection(SseEmitter emitter) {
            this.emitter = emitter;
        }
    }
}
