package com.wewins.fota.adapter.api.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wewins.fota.domain.reporting.model.enums.DeviceUpgradeEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 设备升级上报请求 DTO
 * <p>
 * 用于 /v1/upgrade/report 接口的请求体。
 * 字段与 PRD 定义保持一致。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 * @see <a href="docs/04-technical/upgrade-report-api.md">上报 API 规范文档</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeReportDTO {

    /**
     * 设备 IMEI（15位数字字符串）
     */
    @NotBlank(message = "imei 不能为空")
    @Pattern(regexp = "^\\d{15}$", message = "imei 必须为 15 位数字")
    private String imei;

    /**
     * 请求唯一标识（链路追踪 ID）
     * <p>
     * 从 Check 响应中获取，用于关联检查和上报事件
     * </p>
     */
    @NotBlank(message = "request_id 不能为空")
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 事件类型
     * <p>
     * 设备上报数字：0=DL_START, 1=DL_OK, 2=DL_FAIL, 3=UP_OK, 4=UP_FAIL
     * </p>
     */
    @NotNull(message = "event 不能为空")
    private DeviceUpgradeEventType event;

    /**
     * 扩展详情（错误码、进度等）
     */
    private Map<String, Object> details;
}
