package com.wewins.fota.application.reporting;

import com.wewins.fota.adapter.api.device.dto.UpgradeReportDTO;
import com.wewins.fota.domain.reporting.model.UpgradeReport;
import com.wewins.fota.domain.reporting.service.UpgradeReportGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 设备升级上报应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeReportAppService {

    private final UpgradeReportGateway upgradeReportGateway;

    public void reportUpgrade(UpgradeReport report) {
        if (report == null) {
            return;
        }
        log.info("收到设备升级上报: imei={}, event={}", report.getImei(), report.getEvent());
        upgradeReportGateway.accept(report);
    }

    /**
     * 将 DTO 转换为领域模型
     *
     * @param dto 请求 DTO
     * @param detailsJson 序列化后的 details JSON
     * @return 领域模型
     */
    public UpgradeReport toDomain(UpgradeReportDTO dto, String detailsJson) {
        return UpgradeReport.builder()
                .imei(dto.getImei())
                .event(dto.getEvent())
                .url(dto.getUrl())
                .detailsJson(detailsJson)
                .build();
    }
}
