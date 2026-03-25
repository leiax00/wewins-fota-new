package com.wewins.fota.adapter.api.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 将设备升级检查请求统一归一化为固定字段 + extTags。
 * <p>
 * 约定：
 * <ul>
 *   <li>POST JSON 中未知顶层字段通过 {@link com.fasterxml.jackson.annotation.JsonAnySetter} 自动并入 extTags</li>
 *   <li>GET 请求支持 ext.xxx=yyy 形式</li>
 *   <li>GET 请求中的非核心字段也会自动落入 extTags，便于兼容设备端逐步增加参数</li>
 * </ul>
 * </p>
 */
@Component
public class UpgradeCheckRequestNormalizer {

    private static final String EXT_PREFIX = "ext.";
    private static final Set<String> CORE_PARAMS = Set.of(
            "product", "imei", "version", "auto", "lang", "tag", "dev", "extTags"
    );

    private final ObjectMapper objectMapper;

    public UpgradeCheckRequestNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UpgradeCheckReqDTO normalizeQueryRequest(UpgradeCheckReqDTO request, HttpServletRequest httpRequest) {
        if (request == null) {
            request = new UpgradeCheckReqDTO();
        }

        Map<String, String[]> parameterMap = httpRequest.getParameterMap();
        mergeExtTagsJsonParam(request, parameterMap.get("extTags"));

        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String name = entry.getKey();
            String value = firstNonBlank(entry.getValue());
            if (value == null) {
                continue;
            }

            if (name.startsWith(EXT_PREFIX)) {
                request.putExtTag(name.substring(EXT_PREFIX.length()), value);
                continue;
            }

            if (!CORE_PARAMS.contains(name)) {
                request.putExtTag(name, value);
            }
        }

        return request;
    }

    public UpgradeCheckReqDTO normalizeBodyRequest(UpgradeCheckReqDTO request) {
        return request != null ? request : new UpgradeCheckReqDTO();
    }

    private void mergeExtTagsJsonParam(UpgradeCheckReqDTO request, String[] values) {
        String rawJson = firstNonBlank(values);
        if (rawJson == null) {
            return;
        }

        try {
            JsonNode extTagsNode = objectMapper.readTree(rawJson);
            if (extTagsNode != null && extTagsNode.isObject()) {
                extTagsNode.propertyStream().forEach(entry -> request.putExtTag(entry.getKey(), entry.getValue()));
            }
        } catch (JsonProcessingException ignored) {
            request.putExtTag("raw_extTags", rawJson);
        }
    }

    private String firstNonBlank(String[] values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
