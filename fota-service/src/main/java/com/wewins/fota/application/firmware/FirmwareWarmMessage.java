package com.wewins.fota.application.firmware;

/**
 * CDN 预热相关的消息常量。
 * <p>
 * 统一管理预热错误消息和对应的国际化 messageKey
 * </p>
 */
public final class FirmwareWarmMessage {

    private FirmwareWarmMessage() {
    }

    /**
     * 版本ID无效
     */
    public static final WarmMessage INVALID_VERSION_ID = new WarmMessage(
            "版本ID无效",
            "firmware.warmInvalidVersionId"
    );

    /**
     * 固件版本没有可用的包
     */
    public static final WarmMessage PACKAGE_UNAVAILABLE = new WarmMessage(
            "该固件版本没有可用的包，无法进行预热",
            "firmware.warmUnavailable"
    );

    /**
     * 固件包路径缺失
     */
    public static final WarmMessage PATH_MISSING = new WarmMessage(
            "固件包路径缺失",
            "firmware.warmPathMissing"
    );

    /**
     * 无法生成下载地址
     */
    public static final WarmMessage DOWNLOAD_URL_FAILED = new WarmMessage(
            "无法生成下载地址",
            "firmware.warmDownloadUrlFailed"
    );

    /**
     * 预热失败（通用）
     */
    public static final WarmMessage WARM_FAILED = new WarmMessage(
            "预热失败",
            "firmware.warmFailed"
    );

    /**
     * 根据消息内容查找对应的 WarmMessage。
     *
     * @param message 消息内容
     * @return 匹配的 WarmMessage，如果没有匹配则返回 WARM_FAILED
     */
    public static WarmMessage fromMessage(String message) {
        if (message == null || message.isBlank()) {
            return WARM_FAILED;
        }
        if (INVALID_VERSION_ID.message().equals(message)) {
            return INVALID_VERSION_ID;
        }
        if (PACKAGE_UNAVAILABLE.message().equals(message)) {
            return PACKAGE_UNAVAILABLE;
        }
        if (PATH_MISSING.message().equals(message)) {
            return PATH_MISSING;
        }
        if (DOWNLOAD_URL_FAILED.message().equals(message)) {
            return DOWNLOAD_URL_FAILED;
        }
        return WARM_FAILED;
    }

    /**
     * 预热消息记录。
     *
     * @param message    消息内容（中文）
     * @param messageKey 国际化消息 key
     */
    public record WarmMessage(String message, String messageKey) {
    }
}
