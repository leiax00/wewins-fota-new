package com.wewins.fota.adapter.api.device;

import com.wewins.fota.application.reporting.UpgradeReportAppService;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.domain.reporting.model.UpgradeReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 设备升级状态上报控制器
 * <p>
 * 提供 /v1/upgrade/report 接口，供设备上报升级状态
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@RequestMapping("/v1/upgrade")
@ConditionalOnAppMode({"main", "region"})
@RequiredArgsConstructor
public class UpgradeReportController {

    private final UpgradeReportAppService upgradeReportAppService;

    /**
     * 上报升级状态
     * <p>
     * 接收设备上报的升级事件，发后即忘到 MQ
     * </p>
     *
     * @param requestBody 上报请求体
     * @return 200 OK
     */
    @PostMapping("/report")
    public ResponseEntity<Void> reportUpgrade(@RequestBody UpgradeReportRequestBody requestBody) {
        upgradeReportAppService.reportUpgrade(buildReport(requestBody));

        return ResponseEntity.ok().build();
    }

    /**
     * 构建设备升级上报领域模型
     *
     * @param requestBody 上报请求体
     * @return 上报对象
     */
    private UpgradeReport buildReport(UpgradeReportRequestBody requestBody) {
        return UpgradeReport.builder()
                .imei(requestBody.getImei())
                .currentVersion(requestBody.getCurrentVersion())
                .targetVersion(requestBody.getTargetVersion())
                .eventType(requestBody.getEventType())
                .downloadUrl(requestBody.getDownloadUrl())
                .clientIp(requestBody.getClientIp())
                .userAgent(requestBody.getUserAgent())
                .ext(requestBody.getExt())
                .build();
    }

    /**
     * 设备升级上报请求体
     */
    @lombok.Data
    public static class UpgradeReportRequestBody {
        /**
         * 设备 IMEI
         */
        private String imei;

        /**
         * 当前固件版本
         */
        private String currentVersion;

        /**
         * 目标版本
         */
        private String targetVersion;

        /**
         * 事件类型
         */
        private String eventType;

        /**
         * 下载 URL
         */
        private String downloadUrl;

        /**
         * 客户端 IP
         */
        private String clientIp;

        /**
         * 用户代理
         */
        private String userAgent;

        /**
         * 扩展数据
         */
        private String ext;
    }
}
