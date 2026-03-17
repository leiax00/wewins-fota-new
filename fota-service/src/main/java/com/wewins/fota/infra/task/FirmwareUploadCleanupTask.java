package com.wewins.fota.infra.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 固件上传临时文件清理任务。
 * <p>
 * 定期扫描临时文件目录，清理超过一定时间的孤儿文件。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.firmware.upload.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirmwareUploadCleanupTask {

    private final FirmwareUploadCleanupProperties properties;

    /**
     * 清理过期的临时文件。
     * <p>
     * 执行逻辑：
     * <ol>
     *   <li>扫描临时文件目录（data/storage/tmp）</li>
     *   <li>查找匹配 fw-upload-*.tmp 的文件</li>
     *   <li>删除超过 {@code maxFileAge} 的文件</li>
     * </ol>
     * </p>
     * <p>
     * 清理条件：
     * <ul>
     *   <li>文件名匹配：fw-upload-{uuid}.tmp</li>
     *   <li>文件年龄超过 {@code maxFileAge}（默认 2 小时）</li>
     * </ul>
     * </p>
     */
    @Scheduled(cron = "${app.firmware.upload.cleanup.cron:0 */30 * * * *}")
    public void cleanupExpiredTemporaryFiles() {
        if (!properties.isEnabled()) {
            log.debug("固件上传临时文件清理任务已禁用");
            return;
        }

        Path tempDir = properties.getTempDir();
        if (!Files.exists(tempDir)) {
            log.debug("临时文件目录不存在: {}", tempDir);
            return;
        }

        log.info("开始清理固件上传临时文件: tempDir={}, maxFileAge={}", tempDir, properties.getMaxFileAge());

        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        AtomicLong totalSize = new AtomicLong(0);

        try (Stream<Path> files = Files.list(tempDir)) {
            Instant cutoffTime = Instant.now().minus(properties.getMaxFileAge());

            long scannedCount = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("fw-upload-[a-f0-9-]+\\.tmp"))
                    .filter(path -> {
                        try {
                            Instant fileLastModified = Files.getLastModifiedTime(path).toInstant();
                            return fileLastModified.isBefore(cutoffTime);
                        } catch (IOException e) {
                            log.warn("无法读取文件修改时间: {}", path, e);
                            return false;
                        }
                    })
                    .map(path -> {
                        try {
                            long size = Files.size(path);
                            Files.delete(path);
                            totalSize.addAndGet(size);
                            log.debug("已删除过期临时文件: path={}, age={}, size={}",
                                    path, Duration.between(Files.getLastModifiedTime(path).toInstant(), Instant.now()), size);
                            return 1;
                        } catch (IOException e) {
                            log.warn("删除临时文件失败: {}", path, e);
                            return -1;
                        }
                    })
                    .peek(result -> {
                        if (result == 1) {
                            deletedCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                    })
                    .count();

            if (scannedCount > 0 || deletedCount.get() > 0 || failedCount.get() > 0) {
                log.info("固件上传临时文件清理完成: scanned={}, deleted={}, failed={}, totalSize={}",
                        scannedCount, deletedCount.get(), failedCount.get(), formatSize(totalSize.get()));
            } else {
                log.debug("固件上传临时文件清理完成: 无过期文件");
            }

        } catch (IOException e) {
            log.error("扫描临时文件目录失败: tempDir={}", tempDir, e);
        }
    }

    /**
     * 格式化文件大小。
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
