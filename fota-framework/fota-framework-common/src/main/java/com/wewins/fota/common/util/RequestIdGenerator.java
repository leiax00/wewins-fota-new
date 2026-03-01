package com.wewins.fota.common.util;

import java.util.UUID;

/**
 * 请求 ID 生成工具类
 * <p>
 * 用于生成请求唯一标识，用于关联设备检查请求和后续的升级事件。
 * </p>
 * <p>
 * 生成的 ID 格式：32 字符的 UUID（无中划线），例如：
 * <pre>550e8400e29b41d4a716446655440000</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
public final class RequestIdGenerator {

    private RequestIdGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 生成请求唯一标识
     *
     * @return 32 字符的 UUID 字符串（无中划线）
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
