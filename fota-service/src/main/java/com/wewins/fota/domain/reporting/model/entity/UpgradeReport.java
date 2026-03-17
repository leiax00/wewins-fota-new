package com.wewins.fota.domain.reporting.model.entity;

import com.wewins.fota.domain.reporting.model.enums.DeviceUpgradeEventType;
import lombok.Builder;
import lombok.Value;

/**
 * 设备升级上报领域模型
 * <p>
 * 用于 Domain 层和 Infrastructure 层之间传递上报数据。
 * details 已序列化为 JSON 字符串。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Value
@Builder
public class UpgradeReport {
    /**
     * 设备 IMEI（15位数字字符串）
     */
    String imei;

    /**
     * 请求唯一标识（链路追踪 ID）
     * <p>
     * 从 Check 响应中获取，用于关联检查和上报事件
     * </p>
     */
    String requestId;

    /**
     * 事件类型
     */
    DeviceUpgradeEventType event;

    /**
     * 扩展详情（已序列化的 JSON 字符串）
     */
    String detailsJson;

    /**
     * 客户端 IP 地址
     */
    String clientIp;

    /**
     * 区域标识（main 或 region 节点代码）
     */
    String region;
}
