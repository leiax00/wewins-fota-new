package com.wewins.fota.infra.metrics;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 统一维护设备 API 监控口径，避免 PromQL 和采集过滤条件不一致。
 */
public final class DeviceApiMetricsSupport {

    public static final String DEVICE_CHECK_URI = "/v1/upgrade/check";
    public static final String DEVICE_REPORT_URI = "/v1/upgrade/report";
    public static final String LEGACY_DEVICE_CHECK_URI = "/fota/version/query";
    public static final String DEVICE_API_URI_REGEX = "^/v1/upgrade/(check|report)$";

    private static final Set<String> DEVICE_API_URIS = Set.of(
            DEVICE_CHECK_URI,
            DEVICE_REPORT_URI
    );
    private static final Pattern DEVICE_API_URI_PATTERN = Pattern.compile(DEVICE_API_URI_REGEX);

    private DeviceApiMetricsSupport() {
    }

    public static boolean isDeviceApiUri(String uri) {
        return uri != null && DEVICE_API_URIS.contains(uri);
    }

    public static String canonicalizeDeviceApiUri(String uri) {
        if (uri == null) {
            return null;
        }
        if (LEGACY_DEVICE_CHECK_URI.equals(uri)) {
            return DEVICE_CHECK_URI;
        }
        return uri;
    }

    public static boolean matchesDeviceApiUri(String uri) {
        return uri != null && DEVICE_API_URI_PATTERN.matcher(canonicalizeDeviceApiUri(uri)).matches();
    }
}
