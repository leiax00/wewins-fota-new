package com.wewins.fota.application.reporting.dto;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备升级事件消息体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeEventMessage {

    /**
     * 检查日志（可选）
     */
    private DeviceCheckLog checkLog;

    /**
     * 升级事件列表
     */
    private List<DeviceUpgradeEvent> events;
}
