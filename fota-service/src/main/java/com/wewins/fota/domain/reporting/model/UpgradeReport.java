package com.wewins.fota.domain.reporting.model;

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
     * 事件类型：DL_START, DL_OK, DL_FAIL, UP_OK, UP_FAIL
     */
    String event;

    /**
     * 下载 URL（包含 pid, rid 参数用于溯源）
     */
    String url;

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
