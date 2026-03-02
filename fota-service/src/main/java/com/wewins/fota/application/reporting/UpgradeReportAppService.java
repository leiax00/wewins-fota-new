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
        upgradeReportGateway.accept(report);
    }

    /**
     * 将 DTO 转换为领域模型
     *
     * @param dto 请求 DTO
     * @param detailsJson 序列化后的 details JSON
     * @param clientIp 客户端 IP 地址
     * @param region 区域标识
     * @return 领域模型
     */
    public UpgradeReport toDomain(UpgradeReportDTO dto, String detailsJson, String clientIp, String region) {
        return UpgradeReport.builder()
                .imei(dto.getImei())
                .requestId(dto.getRequestId())
                .event(dto.getEvent())
                .detailsJson(detailsJson)
                .clientIp(clientIp)
                .region(region)
                .build();
    }
}
