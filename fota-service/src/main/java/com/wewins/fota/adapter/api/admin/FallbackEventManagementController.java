package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.infra.persistence.clickhouse.reporting.fallback.FallbackEventReplayService;
import com.wewins.fota.infra.persistence.clickhouse.reporting.fallback.FallbackEventStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 降级事件管理控制器
 * <p>
 * 提供管理端点用于查看和重放降级事件
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@RestController
@RequestMapping("/internal/fallback-events")
@ConditionalOnAppMode("main")
@RequiredArgsConstructor
@ConditionalOnBean(FallbackEventStorage.class)
public class FallbackEventManagementController {

    private final FallbackEventReplayService replayService;
    private final FallbackEventStorage fallbackEventStorage;

    /**
     * 获取降级状态
     *
     * @return 降级状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", fallbackEventStorage.isAvailable());
        status.put("basePath", fallbackEventStorage.getBasePath());
        status.put("filesInfo", getFilesInfo());
        return ResponseEntity.ok(status);
    }

    /**
     * 手动重放今天的降级事件
     *
     * @return 重放结果
     */
    @PostMapping("/replay/today")
    public ResponseEntity<Map<String, Object>> replayToday() {
        int count = replayService.replayFile(getTodayFilePath());

        Map<String, Object> result = new HashMap<>();
        result.put("replayed", count);
        result.put("message", "今天的降级事件重放完成");
        return ResponseEntity.ok(result);
    }

    /**
     * 手动重放所有历史降级事件
     *
     * @return 重放结果
     */
    @PostMapping("/replay/all")
    public ResponseEntity<Map<String, Object>> replayAll() {
        int count = replayService.replayAllHistoryFiles();

        Map<String, Object> result = new HashMap<>();
        result.put("replayed", count);
        result.put("message", "历史降级事件重放完成");
        return ResponseEntity.ok(result);
    }

    /**
     * 获取文件信息
     *
     * @return 文件信息列表
     */
    private Map<String, Object> getFilesInfo() {
        Map<String, Object> info = new HashMap<>();

        String basePath = fallbackEventStorage.getBasePath();
        Path baseDir = Paths.get(basePath);

        if (!Files.exists(baseDir)) {
            info.put("fileCount", 0);
            info.put("totalSizeBytes", 0);
            return info;
        }

        try (Stream<Path> paths = Files.walk(baseDir, 1)) {
            long[] count = {0};
            long[] totalSize = {0};

            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .forEach(p -> {
                        count[0]++;
                        try {
                            totalSize[0] += Files.size(p);
                        } catch (IOException ignored) {
                        }
                    });

            info.put("fileCount", count[0]);
            info.put("totalSizeBytes", totalSize[0]);
            info.put("totalSizeMB", totalSize[0] / 1024.0 / 1024.0);

        } catch (IOException e) {
            log.error("获取降级文件信息失败", e);
            info.put("error", e.getMessage());
        }

        return info;
    }

    private Path getTodayFilePath() {
        String basePath = fallbackEventStorage.getBasePath();
        String date = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        return Paths.get(basePath, "upgrade-events-" + date + ".jsonl");
    }
}
