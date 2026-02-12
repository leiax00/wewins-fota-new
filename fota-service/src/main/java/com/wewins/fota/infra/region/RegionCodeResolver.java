package com.wewins.fota.infra.region;

/**
 * 分区编码解析工具
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
public final class RegionCodeResolver {

    private RegionCodeResolver() {
        // utility class
    }

    public static String resolveRegionCode(String nodeCode) {
        if (nodeCode == null || nodeCode.isBlank()) {
            return "unknown";
        }
        int idx = nodeCode.lastIndexOf('-');
        if (idx <= 0) {
            return nodeCode;
        }
        return nodeCode.substring(0, idx);
    }
}
