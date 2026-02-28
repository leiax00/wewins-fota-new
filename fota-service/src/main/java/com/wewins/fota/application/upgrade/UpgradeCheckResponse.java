package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 设备升级检查响应 DTO
 * <p>
 * 标准响应格式，兼容新老 API
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpgradeCheckResponse {

    /**
     * 是否有更新
     */
    private Boolean hasUpdate;

    /**
     * 检查决策
     * <p>
     * 可选值：
     * <ul>
     *   <li>UPDATE - 有可用更新</li>
     *   <li>NO_UPDATE - 无可用更新</li>
     *   <li>DEVICE_NOT_FOUND - 设备不存在</li>
     *   <li>RATE_LIMITED - 请求被限流</li>
     *   <li>GRAY_MISS - 未命中灰度</li>
     *   <li>ERROR - 错误</li>
     * </ul>
     * </p>
     */
    private String decision;

    /**
     * 目标版本 ID
     */
    private Long targetVersionId;

    /**
     * 目标版本号
     */
    private String targetVersion;

    /**
     * 策略 ID
     */
    private Long policyId;

    /**
     * 建议的下次检查间隔（秒）
     */
    private Integer responseCheckInterval;

    /**
     * 下载延迟（秒）
     * <p>
     * 设备应在等待该时间后才开始下载
     * </p>
     */
    private Integer downloadDelay;

    /**
     * 发布开始时间
     * <p>
     * ISO8601 格式时间字符串
     * </p>
     */
    private String releaseStartDate;

    /**
     * 发布说明（根据设备语言选择）
     */
    private String releaseNote;

    /**
     * 签名下载 URL
     * <p>
     * 包含 policy_id、device_id、expire、sig 参数
     * </p>
     */
    private String downloadUrl;

    /**
     * 固件文件大小（字节）
     */
    private Long fileSize;

    /**
     * 固件文件大小（可读格式）
     * <p>
     * 例如：19MB, 1.5GB
     * </p>
     */
    private String fileSizeText;

    /**
     * 固件校验和
     * <p>
     * MD5 或 SHA-256
     * </p>
     */
    private String checksum;

    /**
     * 校验和算法类型
     * <p>
     * md5 或 sha256
     * </p>
     */
    private String checksumType;

    /**
     * 错误码
     * <p>
     * 仅在决策为 ERROR 时存在
     * </p>
     */
    private String errorCode;

    /**
     * 错误信息
     * <p>
     * 仅在决策为 ERROR 或 DEVICE_NOT_FOUND 时存在
     * </p>
     */
    private String errorMessage;

    /**
     * 控制参数（扩展对象）
     * <p>
     * 包含额外的控制参数
     * </p>
     */
    private ControlParams control;

    /**
     * 扩展数据
     * <p>
     * 用于未来扩展的额外字段
     * </p>
     */
    private Map<String, Object> ext;

    /**
     * 控制参数嵌套对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ControlParams {
        /**
         * 建议的下次检查间隔（秒）
         */
        private Integer checkInterval;

        /**
         * 下载延迟（秒）
         */
        private Integer downloadDelay;

        /**
         * 是否强制升级
         */
        private Boolean forceUpgrade;

        /**
         * 是否需要重启
         */
        private Boolean requireReboot;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建"无更新"响应
     */
    public static UpgradeCheckResponse noUpdate(Integer checkInterval) {
        return UpgradeCheckResponse.builder()
                .hasUpdate(false)
                .decision("NO_UPDATE")
                .responseCheckInterval(checkInterval != null ? checkInterval : 86400)
                .build();
    }

    /**
     * 创建"设备不存在"响应
     */
    public static UpgradeCheckResponse notFound(String message) {
        return UpgradeCheckResponse.builder()
                .hasUpdate(false)
                .decision("DEVICE_NOT_FOUND")
                .errorMessage(message)
                .build();
    }

    /**
     * 创建"限流"响应
     */
    public static UpgradeCheckResponse rateLimited(String message, int retryAfterSeconds) {
        return UpgradeCheckResponse.builder()
                .hasUpdate(false)
                .decision("RATE_LIMITED")
                .errorMessage(message)
                .responseCheckInterval(retryAfterSeconds)
                .downloadDelay(retryAfterSeconds)
                .build();
    }

    /**
     * 创建"错误"响应
     */
    public static UpgradeCheckResponse error(String errorCode, String message) {
        return UpgradeCheckResponse.builder()
                .hasUpdate(false)
                .decision("ERROR")
                .errorCode(errorCode)
                .errorMessage(message)
                .build();
    }
}
