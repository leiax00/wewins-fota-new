package com.wewins.fota.application.reporting;

import com.wewins.fota.application.reporting.dto.UpgradeEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 升级事件入站应用服务。
 */
@Service
@RequiredArgsConstructor
public class UpgradeEventIngestAppService {

    private final DeviceUpgradeEventAppService deviceUpgradeEventAppService;

    public void ingest(UpgradeEventMessage eventMessage) {
        if (eventMessage == null) {
            return;
        }

        if (eventMessage.getCheckLog() != null) {
            deviceUpgradeEventAppService.recordCheckLog(eventMessage.getCheckLog());
        }

        if (eventMessage.getEvents() != null && !eventMessage.getEvents().isEmpty()) {
            deviceUpgradeEventAppService.recordUpgradeEvents(eventMessage.getEvents());
        }
    }
}
