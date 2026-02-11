package com.wewins.fota.adapter.api.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 跨区域数据接入控制器
 * <p>
 * 接收区域转发来的汇总数据并写入主区域数据库
 * </p>
 *
 * 仅在 main 模式下可用
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/internal/ingest")
@ConditionalOnProperty(name = "app.mode", havingValue = "main")
@RequiredArgsConstructor
public class IngestController {

    /**
     * 接收区域汇总数据
     * <p>
     * 批量接收区域上报的设备和事件数据
     * </p>
     *
     * @param data 汇总数据
     * @return 200 OK
     */
    @PostMapping
    public ResponseEntity<Void> ingestData(@RequestBody Map<String, Object> data) {

        log.info("收到区域汇总数据: records={}",
                data.containsKey("records") ? ((Map<?, ?>) data.get("records")).size() : 0);

        // TODO: 实现数据写入逻辑
        // 1. 验证数据格式
        // 2. 批量写入 PostgreSQL 或 ClickHouse
        // 3. 返回处理结果

        return ResponseEntity.ok().build();
    }
}

