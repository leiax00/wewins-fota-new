package com.wewins.fota.application.upgrade.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.common.enums.CheckMode;
import lombok.Data;

/**
 * 设备升级检查请求 DTO
 * <p>
 * 用于 GET/POST /v1/upgrade/check 接口的请求参数
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Data
public class UpgradeCheckReqDTO {

    /**
     * 产品名称（必选）
     */
    private String product;

    /**
     * 设备 IMEI（必选）
     */
    private String imei;

    /**
     * 当前固件版本（必选）
     */
    private String version;

    /**
     * 检查模式：0=手动, 1=自动（默认自动）
     */
    private Integer auto = 1;

    /**
     * 获取检查模式枚举
     * <p>
     * 默认返回自动检查模式
     * </p>
     */
    public CheckMode getCheckMode() {
        if (auto == null || auto == 1) {
            return CheckMode.AUTO;
        }
        return CheckMode.MANUAL;
    }

    /**
     * 语言代码
     */
    private String lang;

    /**
     * 设备标签
     */
    private String tag;

    /**
     * 是否开发环境（1: 是, 不填或0: 否，默认 0）
     */
    private Integer dev = 0;

    /**
     * 扩展标签（JSON 对象，仅 POST）
     */
    private JsonNode extTags;
}
