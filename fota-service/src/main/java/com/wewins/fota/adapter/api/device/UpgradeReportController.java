package com.wewins.fota.adapter.api.device;

import com.wewins.fota.analytics.entity.DeviceUpgradeEvent;
import com.wewins.fota.analytics.service.DeviceUpgradeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.features.device-api", havingValue = "true")
@RequiredArgsConstructor
public class UpgradeReportController {

    private final DeviceUpgradeEventService deviceUpgradeEventService;

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

        log.info("收到设备升级上报: imei={}, eventType={}",
                requestBody.getImei(), requestBody.getEventType());

        // TODO: 构建事件记录并发送到 MQ
        // 当前直接记录到 ClickHouse

        // deviceUpgradeEventService.recordUpgradeEvent(buildEvent(requestBody));

        return ResponseEntity.ok().build();
    }

    /**
     * 构建设备升级事件
     *
     * @param requestBody 上报请求体
     * @return 事件对象
     */
    private DeviceUpgradeEvent buildEvent(UpgradeReportRequestBody requestBody) {
        // TODO: 根据请求体构建完整的事件对象
        // 当前仅记录基础信息
        return null;
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
