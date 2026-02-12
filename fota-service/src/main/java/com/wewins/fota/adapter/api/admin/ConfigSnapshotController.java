package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.region.RegionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 配置快照拉取控制器
 * <p>
 * 提供主区域配置快照接口，供区域拉取完整配置
 * </p>
 *
 * 快照类型：
 * - policy: 升级策略快照
 * - product: 产品快照
 * - control: 控制规则快照
 *
 * 仅在 main 模式下可用
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/internal/config")
@ConditionalOnProperty(name = "app.mode", havingValue = "main")
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RequiredArgsConstructor
public class ConfigSnapshotController {

    private final RegionSyncService regionSyncService;

    /**
     * 获取配置快照
     * <p>
     * 区域拉取指定类型的配置快照
     * </p>
     *
     * @param snapshotType 快照类型（policy/product/control）
     * @return 快照数据
     */
    @GetMapping("/snapshot/{snapshotType}")
    public ResponseEntity<Map<String, Object>> getSnapshot(
            @PathVariable String snapshotType) {

        log.debug("拉取配置快照: type={}", snapshotType);

        Map<String, Object> snapshot = regionSyncService.fetchSnapshot(snapshotType);

        return ResponseEntity.ok(snapshot);
    }
}
