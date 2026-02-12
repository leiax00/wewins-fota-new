package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.application.region.RegionSyncService;
import com.wewins.fota.infra.security.RegionRotateKey;
import com.wewins.fota.infra.security.RegionRotateKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 配置版本轮询控制器
 * <p>
 * 提供主区域配置版本查询接口，供区域定期轮询
 * </p>
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
public class ConfigVersionController {

    private final RegionSyncService regionSyncService;
    private final RegionRotateKeyService rotateKeyService;

    /**
     * 获取配置版本
     * <p>
     * 区域定期调用此接口检查配置是否更新
     * </p>
     *
     * @return 当前配置版本号
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersion(
            @RequestHeader(value = "X-Region-Code", required = false) String regionCode,
            @RequestHeader(value = "X-Secret-Ack", required = false) String secretAck
    ) {
        long version = regionSyncService.getLocalConfigVersion();
        log.debug("查询配置版本: {}", version);

        if (regionCode != null && !regionCode.isBlank() && secretAck != null && !secretAck.isBlank()) {
            rotateKeyService.acknowledgeRotateKey(regionCode, secretAck);
        }

        RegionRotateKey rotateKey = null;
        if (regionCode != null && !regionCode.isBlank()) {
            rotateKey = rotateKeyService.getPendingRotateKey(regionCode);
        }

        if (rotateKey == null) {
            return ResponseEntity.ok(Map.of("version", version));
        }

        Map<String, Object> payload = Map.of(
                "version", version,
                "rotateKey", Map.of(
                        "keyId", rotateKey.getKeyId(),
                        "secret", rotateKey.getSecret()
                )
        );
        return ResponseEntity.ok(payload);
    }
}
