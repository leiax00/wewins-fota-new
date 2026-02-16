package com.wewins.fota.application.reporting;

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
        log.info("收到设备升级上报: imei={}, eventType={}", report.getImei(), report.getEventType());
        upgradeReportGateway.accept(report);
    }
}
