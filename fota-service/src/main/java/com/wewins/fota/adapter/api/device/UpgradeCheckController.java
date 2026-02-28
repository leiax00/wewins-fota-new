package com.wewins.fota.adapter.api.device;

import com.wewins.fota.application.upgrade.UpgradeCheckService;
import com.wewins.fota.application.upgrade.UpgradeCheckService.CheckResult;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 设备升级检查控制器
 * <p>
 * 提供 /v1/upgrade/check 接口，供设备检查是否有可用更新
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@ConditionalOnAppMode({"main", "region"})
@RequiredArgsConstructor
public class UpgradeCheckController {

    private final UpgradeCheckService upgradeCheckService;

    /**
     * 检查设备升级（GET 方法）
     * <p>
     * 简单的 GET 请求，设备通过查询参数传递信息
     * </p>
     *
     * @param imei 设备 IMEI
     * @param version 当前固件版本
     * @param language 语言设置
     * @param tags 设备标签（JSON 字符串）
     * @return 检查结果
     */
    @GetMapping("/v1/upgrade/check")
    public ResponseEntity<CheckResult> checkUpgrade(
            @RequestParam String imei,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String tags) {

        log.debug("收到设备检查请求: imei={}, version={}", imei, version);

        CheckResult result = upgradeCheckService.checkUpgrade(imei);

        return ResponseEntity.ok(result);
    }

    /**
     * 兼容老系统的版本检测
     */
    @GetMapping("/fota/version/query")
    public ResponseEntity<CheckResult> checkUpgradeOld(
            @RequestParam String imei,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String tags
    ) {
        return checkUpgrade(imei, version, language, tags);
    }

    /**
     * 检查设备升级（POST 方法）
     * <p>
     * 支持 POST 请求，可传递扩展载荷（如设备标签、环境信息等）
     * </p>
     *
     * @param requestBody 扩展请求体
     * @return 检查结果
     */
    @PostMapping("/v1/upgrade/check")
    public ResponseEntity<CheckResult> checkUpgradePost(@RequestBody UpgradeCheckRequestBody requestBody) {

        log.debug("收到设备检查 POST 请求: imei={}", requestBody.getImei());

        CheckResult result = upgradeCheckService.checkUpgrade(requestBody.getImei());

        return ResponseEntity.ok(result);
    }

    /**
     * 设备检查请求体（POST 方法）
     */
    @lombok.Data
    public static class UpgradeCheckRequestBody {
        /**
         * 设备 IMEI
         */
        private String imei;

        /**
         * 当前固件版本
         */
        private String version;

        /**
         * 语言设置
         */
        private String language;

        /**
         * 设备标签（JSON 字符串）
         */
        private String tags;
    }
}
