package com.wewins.fota.common.region;

/**
 * Resolver for region code from node code.
 */
public final class RegionCodeResolver {

    private RegionCodeResolver() {
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
