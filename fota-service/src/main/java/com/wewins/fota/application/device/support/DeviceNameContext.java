package com.wewins.fota.application.device.support;

import java.util.Map;

public record DeviceNameContext(
        Map<Long, String> productNameMap,
        Map<Long, String> versionNameMap,
        Map<Long, String> batchNameMap
) {
}
