package com.wewins.fota.adapter.api.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.adapter.api.device.dto.UpgradeReportDTO;
import com.wewins.fota.application.reporting.UpgradeReportAppService;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.util.HttpUtils;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.model.entity.UpgradeReport;
import com.wewins.fota.infra.metrics.FotaMetrics;
import com.wewins.fota.infra.metrics.NodeIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备升级状态上报控制器
 * <p>
 * 提供 /v1/upgrade/report 接口，供设备上报升级状态
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 * @see <a href="docs/04-technical/upgrade-report-api.md">上报 API 规范文档</a>
 */
@Slf4j
@RestController
@RequestMapping("/v1/upgrade")
@ConditionalOnAppMode({"main", "region"})
@RequiredArgsConstructor
public class UpgradeReportController {

    private final UpgradeReportAppService upgradeReportAppService;
    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;
    private final ProductRepository productRepository;
    private final FotaMetrics fotaMetrics;
    private final NodeIdentity nodeIdentity;

    /**
     * 上报升级状态
     * <p>
     * 接收设备上报的升级事件，发后即忘到 MQ
     * </p>
     *
     * @param requestBody 上报请求体
     * @param httpRequest HTTP 请求（用于提取客户端 IP）
     * @return 200 OK
     */
    @PostMapping("/report")
    public ResponseEntity<Void> reportUpgrade(
            @Valid @RequestBody UpgradeReportDTO requestBody,
            HttpServletRequest httpRequest) {
        String detailsJson = serializeDetails(requestBody.getDetails());
        String clientIp = HttpUtils.extractClientIp(httpRequest);
        UpgradeReport report = upgradeReportAppService.toDomain(
                requestBody,
                detailsJson,
                clientIp,
                nodeIdentity.regionCode()
        );
        upgradeReportAppService.reportUpgrade(report);
        fotaMetrics.recordUpgradeEvent(resolveProductModel(requestBody.getImei()), requestBody.getEvent().name());

        return ResponseEntity.ok().build();
    }

    private String resolveProductModel(String imei) {
        return deviceRepository.findByImei(imei)
                .flatMap(device -> productRepository.findById(device.getProductId()))
                .map(product -> product.getModel())
                .orElse("unknown");
    }

    /**
     * 序列化 details Map 为 JSON 字符串
     *
     * @param details 详情 Map
     * @return JSON 字符串，序列化失败时返回 null
     */
    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("序列化 details 失败: {}", e.getMessage());
            return null;
        }
    }
}
