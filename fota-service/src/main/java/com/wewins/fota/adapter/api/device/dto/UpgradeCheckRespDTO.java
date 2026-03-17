package com.wewins.fota.adapter.api.device.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 设备升级检查响应 DTO（API 层对外响应）
 * <p>
 * 所有字段统一使用 snake_case 风格
 * </p>
 * <p>
 * 响应格式示例：
 * <pre>
 * {
 *    "code": 0,
 *    "request_id": "550e8400-e29b-41d4-a716-446655440000",
 *    "release_start_date": "2026-02-03T10:28:56",
 *    "release_note": "xxxxxxx",
 *    "new_firmware": "v2.0.0",
 *    "download_url": "https://cdn.xxx.com/pkg.bin?sig=...",
 *    "file_size": 20000000,
 *    "file_size_text": "19MB",
 *    "control": {
 *       "check_interval": 86400,
 *       "download_delay": 300
 *    }
 * }
 * </pre>
 * </p>
 * <p>
 * code 字段说明：
 * <ul>
 *   <li>0 - 有可用更新</li>
 *   <li>1 - 无更新</li>
 *   <li>2 - 请求被限流</li>
 *   <li>3 - 设备不存在</li>
 *   <li>4 - 错误</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpgradeCheckRespDTO {

    /**
     * 决策代码
     * <ul>
     *   <li>0 - 有可用更新</li>
     *   <li>1 - 无更新</li>
     *   <li>2 - 请求被限流</li>
     *   <li>3 - 设备不存在</li>
     *   <li>4 - 错误</li>
     * </ul>
     */
    @JsonProperty("code")
    private Integer code;

    /**
     * 请求唯一标识（链路追踪 ID）
     * <p>
     * 设备需在后续上报请求中携带此 ID，用于关联检查和上报事件
     * </p>
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 发布开始日期（ISO 8601 格式）
     */
    @JsonProperty("release_start_date")
    private String releaseStartDate;

    /**
     * 发布说明
     */
    @JsonProperty("release_note")
    private String releaseNote;

    /**
     * 新固件版本号
     */
    @JsonProperty("new_firmware")
    private String newFirmware;

    /**
     * 签名下载 URL
     */
    @JsonProperty("download_url")
    private String downloadUrl;

    /**
     * 文件大小（字节）
     */
    @JsonProperty("file_size")
    private Long fileSize;

    /**
     * 文件大小文本（如 "19MB"）
     */
    @JsonProperty("file_size_text")
    private String fileSizeText;

    /**
     * 校验和（用于固件完整性验证）
     */
    private String checksum;

    /**
     * 校验和类型（sha256 或 md5）
     */
    @JsonProperty("checksum_type")
    private String checksumType;

    /**
     * 控制参数
     */
    @JsonProperty("control")
    private Control control;

    /**
     * 控制参数嵌套对象
     */
    @Data
    @Builder
    public static class Control {
        /**
         * 下次检查间隔（秒）
         */
        @JsonProperty("check_interval")
        private Integer checkInterval;

        /**
         * 下载延迟（秒）
         */
        @JsonProperty("download_delay")
        private Integer downloadDelay;
    }
}
