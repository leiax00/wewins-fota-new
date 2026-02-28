package com.wewins.fota.infra.persistence.clickhouse.reporting.fallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 事件本地文件降级存储
 * <p>
 * 当 ClickHouse 不可用时，将事件写入本地文件
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>按日期分文件存储，便于后续重放</li>
 *   <li>使用 JSON Lines 格式，每行一个事件 JSON</li>
 *   <li>使用 ReentrantLock 保证并发安全</li>
 *   <li>文件路径可配置，默认为 data/fallback/events</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.clickhouse.fallback", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FallbackEventStorage {

    private final ObjectMapper objectMapper;

    @Value("${app.clickhouse.fallback.base-path:data/fallback/events}")
    private String basePath;

    @Value("${app.clickhouse.fallback.max-file-size-mb:100}")
    private int maxFileSizeMb;

    private final Lock writeLock = new ReentrantLock();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 写入单个事件到本地文件
     *
     * @param event 升级事件
     * @return true=写入成功，false=写入失败
     */
    public boolean writeEvent(DeviceUpgradeEvent event) {
        return writeEvents(List.of(event));
    }

    /**
     * 批量写入事件到本地文件
     *
     * @param events 升级事件列表
     * @return true=写入成功，false=写入失败
     */
    public boolean writeEvents(List<DeviceUpgradeEvent> events) {
        if (events == null || events.isEmpty()) {
            return true;
        }

        writeLock.lock();
        try {
            Path filePath = getTodayFilePath();
            ensureDirectoryExists(filePath.getParent());

            // 检查文件大小，避免单个文件过大
            if (Files.exists(filePath)) {
                long fileSizeBytes = Files.size(filePath);
                long maxSizeBytes = maxFileSizeMb * 1024L * 1024L;
                if (fileSizeBytes > maxSizeBytes) {
                    log.warn("降级文件过大，创建新文件: filePath={}, size={}MB",
                            filePath, fileSizeBytes / 1024 / 1024);
                    filePath = getTimestampedFilePath();
                }
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                    filePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                for (DeviceUpgradeEvent event : events) {
                    String json = objectMapper.writeValueAsString(event);
                    writer.write(json);
                    writer.newLine();
                }
            }

            log.info("事件已写入降级文件: count={}, filePath={}", events.size(), filePath);
            return true;

        } catch (IOException e) {
            log.error("写入降级文件失败: count={}", events.size(), e);
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 获取今天的文件路径
     * <p>
     * 格式：{basePath}/upgrade-events-{yyyy-MM-dd}.jsonl
     * </p>
     *
     * @return 文件路径
     */
    private Path getTodayFilePath() {
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return Paths.get(basePath, "upgrade-events-" + date + ".jsonl");
    }

    /**
     * 获取带时间戳的文件路径
     * <p>
     * 用于文件过大时创建新文件
     * </p>
     *
     * @return 文件路径
     */
    private Path getTimestampedFilePath() {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        );
        return Paths.get(basePath, "upgrade-events-" + timestamp + ".jsonl");
    }

    /**
     * 确保目录存在
     *
     * @param directory 目录路径
     * @throws IOException 创建目录失败
     */
    private void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            log.info("创建降级文件目录: {}", directory);
        }
    }

    /**
     * 获取降级存储基础路径
     *
     * @return 基础路径
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * 检查降级存储是否可用
     *
     * @return true=可用，false=不可用
     */
    public boolean isAvailable() {
        try {
            Path path = Paths.get(basePath);
            return Files.isWritable(path) || Files.isWritable(path.getParent());
        } catch (Exception e) {
            log.error("检查降级存储可用性失败", e);
            return false;
        }
    }
}
