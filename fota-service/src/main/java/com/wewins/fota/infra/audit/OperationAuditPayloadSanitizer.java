package com.wewins.fota.infra.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Array;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OperationAuditPayloadSanitizer {

    private static final int MAX_JSON_CHARS = 64 * 1024;

    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password",
            "passwordhash",
            "oldpassword",
            "newpassword",
            "confirmpassword",
            "token",
            "authorization",
            "secret",
            "accesskey",
            "secretkey"
    );

    private final ObjectMapper objectMapper;

    public JsonNode sanitizeRequestBody(Object[] args) {
        List<Object> bodies = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null
                    || arg instanceof HttpServletRequest
                    || arg instanceof ServletResponse
                    || arg instanceof BindingResult) {
                continue;
            }
            if (isSimpleScalar(arg)) {
                continue;
            }
            Object sanitized = sanitizeValue(arg, 0);
            if (sanitized != null) {
                bodies.add(sanitized);
            }
        }
        if (bodies.isEmpty()) {
            return null;
        }
        Object payload = bodies.size() == 1 ? bodies.get(0) : bodies;
        return toLimitedJsonNode(payload);
    }

    public JsonNode sanitizeQuery(Map<String, Object> query) {
        return toLimitedJsonNode(sanitizeValue(query, 0));
    }

    public String extractCandidateText(JsonNode node, List<String> fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.get(fieldName);
            if (candidate != null && !candidate.isNull() && !candidate.asText().isBlank()) {
                return candidate.asText();
            }
        }
        return null;
    }

    public JsonNode toLimitedJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode node = objectMapper.valueToTree(value);
        String serialized = node.toString();
        if (serialized.length() <= MAX_JSON_CHARS) {
            return node;
        }
        return objectMapper.valueToTree(Map.of(
                "truncated", true,
                "originalLength", serialized.length(),
                "content", serialized.substring(0, MAX_JSON_CHARS)
        ));
    }

    private Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > 4) {
            return String.valueOf(value);
        }
        if (value instanceof MultipartFile file) {
            return Map.of(
                    "fileName", file.getOriginalFilename(),
                    "size", file.getSize(),
                    "contentType", file.getContentType()
            );
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof Temporal) {
            return value;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(sanitizeValue(Array.get(value, i), depth + 1));
            }
            return items;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> items = new ArrayList<>(collection.size());
            for (Object item : collection) {
                items.add(sanitizeValue(item, depth + 1));
            }
            return items;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sanitized.put(key, isSensitiveKey(key) ? "***" : sanitizeValue(entry.getValue(), depth + 1));
            }
            return sanitized;
        }

        Map<String, Object> map = objectMapper.convertValue(value, Map.class);
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            sanitized.put(entry.getKey(), isSensitiveKey(entry.getKey()) ? "***" : sanitizeValue(entry.getValue(), depth + 1));
        }
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.trim().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.contains(normalized);
    }

    private boolean isSimpleScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>;
    }
}
