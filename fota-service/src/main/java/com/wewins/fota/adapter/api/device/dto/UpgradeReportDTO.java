package com.wewins.fota.adapter.api.device.dto;

import jakarta.validation.constraints.NotBlank;
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
     * 事件类型：DL_START, DL_OK, DL_FAIL, UP_OK, UP_FAIL
     */
    @NotBlank(message = "event 不能为空")
    @Pattern(regexp = "^(DL_START|DL_OK|DL_FAIL|UP_OK|UP_FAIL)$",
             message = "event 必须为有效的事件类型: DL_START, DL_OK, DL_FAIL, UP_OK, UP_FAIL")
    private String event;

    /**
     * 下载 URL（包含 pid, rid 参数用于溯源）
     */
    @NotBlank(message = "url 不能为空")
    private String url;

    /**
     * 扩展详情（错误码、进度等）
     */
    private Map<String, Object> details;
}
