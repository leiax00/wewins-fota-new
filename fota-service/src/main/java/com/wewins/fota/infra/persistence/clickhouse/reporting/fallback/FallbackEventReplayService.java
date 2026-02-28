package com.wewins.fota.infra.persistence.clickhouse.reporting.fallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 降级事件重放服务
 * <p>
 * 定期从本地文件读取降级存储的事件，重新写入 ClickHouse
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>定时任务，默认每 5 分钟执行一次</li>
 *   <li>只处理今天的文件，避免重复处理旧文件</li>
 *   <li>处理成功后删除源文件</li>
 *   <li>可配置是否启用定时重放</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.clickhouse.fallback.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FallbackEventReplayService {

    private final ObjectMapper objectMapper;
    private final FallbackEventWriter fallbackEventWriter;

    @Value("${app.clickhouse.fallback.base-path:data/fallback/events}")
    private String basePath;

    @Value("${app.clickhouse.fallback.replay.batch-size:500}")
    private int replayBatchSize;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 定时重放任务
     * <p>
     * 每 5 分钟执行一次，重放今天的事件文件
     * </p>
     */
    @Scheduled(fixedDelayString = "${app.clickhouse.fallback.replay.interval-ms:300000}")
    public void replayTodayEvents() {
        try {
            Path todayFile = getTodayFilePath();
            if (!Files.exists(todayFile)) {
                log.debug("今天的降级文件不存在，跳过重放");
                return;
            }

            long fileSize = Files.size(todayFile);
            if (fileSize == 0) {
                log.debug("今天的降级文件为空，跳过重放");
                return;
            }

            log.info("开始重放降级事件: filePath={}, size={}KB", todayFile, fileSize / 1024);
            int replayedCount = replayFile(todayFile);

            if (replayedCount > 0) {
                log.info("降级事件重放完成: replayed={}", replayedCount);
            }

        } catch (Exception e) {
            log.error("重放降级事件失败", e);
        }
    }

    /**
     * 重放指定文件
     *
     * @param filePath 文件路径
     * @return 重放的事件数量
     */
    public int replayFile(Path filePath) {
        List<DeviceUpgradeEvent> events = readEventsFromFile(filePath);
        if (events.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        int totalBatches = (events.size() + replayBatchSize - 1) / replayBatchSize;

        for (int i = 0; i < events.size(); i += replayBatchSize) {
            int end = Math.min(i + replayBatchSize, events.size());
            List<DeviceUpgradeEvent> batch = events.subList(i, end);

            try {
                fallbackEventWriter.writeToClickHouse(batch);
                successCount += batch.size();

                // 处理成功后删除已处理的行（简化：全部处理完删除文件）
                if (end >= events.size()) {
                    deleteFile(filePath);
                }

            } catch (Exception e) {
                log.error("批次写入 ClickHouse 失败: batch={}/{}, batchSize={}",
                        i / replayBatchSize + 1, totalBatches, batch.size(), e);
                // 部分失败，保留文件等待下次重试
                break;
            }
        }

        return successCount;
    }

    /**
     * 从文件读取事件
     *
     * @param filePath 文件路径
     * @return 事件列表
     */
    private List<DeviceUpgradeEvent> readEventsFromFile(Path filePath) {
        List<DeviceUpgradeEvent> events = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            int lineNumber = 0;
            int errorCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    DeviceUpgradeEvent event = objectMapper.readValue(line, DeviceUpgradeEvent.class);
                    events.add(event);
                } catch (Exception e) {
                    errorCount++;
                    if (errorCount <= 10) {
                        log.error("解析事件失败: line={}, content={}", lineNumber, line, e);
                    }
                }
            }

            if (errorCount > 10) {
                log.warn("共有 {} 行解析失败", errorCount);
            }

        } catch (IOException e) {
            log.error("读取降级文件失败: filePath={}", filePath, e);
        }

        return events;
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径
     */
    private void deleteFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
            log.info("已删除重放完成的降级文件: {}", filePath);
        } catch (IOException e) {
            log.error("删除降级文件失败: filePath={}", filePath, e);
        }
    }

    /**
     * 获取今天的文件路径
     */
    private Path getTodayFilePath() {
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return Paths.get(basePath, "upgrade-events-" + date + ".jsonl");
    }

    /**
     * 手动触发重放所有历史文件
     * <p>
     * 用于管理员手动重放历史降级事件
     * </p>
     *
     * @return 重放的事件总数
     */
    public int replayAllHistoryFiles() {
        Path baseDir = Paths.get(basePath);

        if (!Files.exists(baseDir)) {
            log.info("降级文件目录不存在: {}", baseDir);
            return 0;
        }

        try (Stream<Path> paths = Files.walk(baseDir, 1)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .filter(p -> !p.equals(getTodayFilePath())) // 排除今天的文件（由定时任务处理）
                    .mapToInt(this::replayFile)
                    .sum();

        } catch (IOException e) {
            log.error("重放历史文件失败", e);
            return 0;
        }
    }
}
