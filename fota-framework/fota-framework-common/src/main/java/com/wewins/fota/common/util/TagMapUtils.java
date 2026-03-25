package com.wewins.fota.common.util;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 标签 Map 归一化与比较工具。
 */
public final class TagMapUtils {

    private TagMapUtils() {
    }

    public static Map<String, String> normalize(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String value = normalizeValue(entry.getValue());
            if (value != null) {
                normalized.put(key.trim(), value);
            }
        }
        return normalized;
    }

    public static boolean equalsOnKeys(Map<String, String> left, Map<String, String> right, Set<String> keys) {
        Map<String, String> normalizedLeft = normalize(left);
        Map<String, String> normalizedRight = normalize(right);

        if (keys == null || keys.isEmpty()) {
            return Objects.equals(normalizedLeft, normalizedRight);
        }

        for (String key : keys) {
            if (!Objects.equals(normalizedLeft.get(key), normalizedRight.get(key))) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
