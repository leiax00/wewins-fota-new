package com.wewins.fota.infra.config.web;

import java.time.ZoneId;
import java.util.Optional;

/**
 * 时区上下文（ThreadLocal）
 * <p>
 * 存储当前请求的客户端时区信息
 * </p>
 */
public class TimeZoneContext {

    private static final ThreadLocal<ZoneId> TIME_ZONE = new ThreadLocal<>();
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("UTC");

    /**
     * 设置当前请求的时区
     *
     * @param zoneId 时区
     */
    public static void setTimeZone(ZoneId zoneId) {
        TIME_ZONE.set(zoneId);
    }

    /**
     * 获取当前请求的时区
     *
     * @return 时区，如果未设置则返回 UTC
     */
    public static ZoneId getTimeZone() {
        return Optional.ofNullable(TIME_ZONE.get()).orElse(DEFAULT_TIME_ZONE);
    }

    /**
     * 清除当前请求的时区
     */
    public static void clear() {
        TIME_ZONE.remove();
    }
}
