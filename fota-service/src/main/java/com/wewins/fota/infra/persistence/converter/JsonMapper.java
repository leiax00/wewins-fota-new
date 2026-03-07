package com.wewins.fota.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.domain.base.vo.JsonValue;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.policy.model.enums.TimeWindowType;
import com.wewins.fota.domain.policy.model.vo.PolicyTimeWindow;
import io.lettuce.core.json.JsonObject;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class JsonMapper {

    private final ObjectMapper objectMapper;

    public JsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Named("toJsonNode")
    public JsonNode toJsonNode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("FirmwareVersion JSON 反序列化失败", e);
        }
    }

    @Named("toJsonString")
    public String toJsonString(JsonNode value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("FirmwareVersion JSON 序列化失败", e);
        }
    }

    @Named("toDeviceVersionParts")
    public DeviceVersionParts toDeviceVersionParts(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, DeviceVersionParts.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DeviceVersionParts JSON 反序列化失败", e);
        }
    }

    @Named("toDeviceVersionPartsString")
    public String toDeviceVersionPartsString(DeviceVersionParts value) {
        if (value == null) {
            return null;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode partsNode = objectMapper.createObjectNode();
            if (value.hasVersion()) {
                value.getParts().forEach((partName, part) -> {
                    if (part != null) {
                        ObjectNode partNode = objectMapper.createObjectNode();
                        Long versionId = part.getVersionId();
                        if (versionId != null) {
                            partNode.put("versionId", versionId);
                        }
                        LocalDateTime updatedAt = part.getUpdatedAt();
                        if (updatedAt != null) {
                            partNode.put("updatedAt", updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        }
                        partsNode.set(partName, partNode);
                    }
                });
            }

            root.set("parts", partsNode);
            if (StringUtils.hasText(value.getPrimaryPart())) {
                root.put("primaryPart", value.getPrimaryPart());
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DeviceVersionParts JSON 序列化失败", e);
        }
    }

    @Named("toJsonValue")
    public JsonValue toJsonValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new JsonValue(value);
    }

    @Named("toJsonValueString")
    public String toJsonValueString(JsonValue value) {
        if (value == null || !StringUtils.hasText(value.getValue())) {
            return null;
        }
        return value.getValue();
    }

    @Named("toLongSet")
    public Set<Long> toLongSet(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new LinkedHashSet<>(objectMapper.readValue(value, new TypeReference<Set<Long>>() {
            }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Long Set JSON 反序列化失败", e);
        }
    }

    @Named("toLongSetString")
    public String toLongSetString(Set<Long> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Long Set JSON 序列化失败", e);
        }
    }

    @Named("toStringSet")
    public Set<String> toStringSet(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new LinkedHashSet<>(objectMapper.readValue(value, new TypeReference<Set<String>>() {
            }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String Set JSON 反序列化失败", e);
        }
    }

    @Named("toStringSetString")
    public String toStringSetString(Set<String> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String Set JSON 序列化失败", e);
        }
    }

    @Named("toTagMap")
    public Map<String, Object> toTagMap(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Tag Map JSON 反序列化失败", e);
        }
    }

    @Named("toTagMapString")
    public String toTagMapString(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Tag Map JSON 序列化失败", e);
        }
    }

    @Named("toStringMap")
    public Map<String, String> toStringMap(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String Map JSON 反序列化失败", e);
        }
    }

    @Named("toStringMapString")
    public String toStringMapString(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String Map JSON 序列化失败", e);
        }
    }

    @Named("toPolicyTimeWindow")
    public PolicyTimeWindow toPolicyTimeWindow(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            String typeValue = node.path("type").asText(null);
            TimeWindowType type = typeValue == null ? null : TimeWindowType.of(typeValue);
            LocalDateTime startAt = parseNullableLocalDateTime(node.path("startAt").asText(null));
            LocalDateTime endAt = parseNullableLocalDateTime(node.path("endAt").asText(null));
            return PolicyTimeWindow.builder().type(type).startAt(startAt).endAt(endAt).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PolicyTimeWindow JSON 反序列化失败", e);
        }
    }

    @Named("toPolicyTimeWindowString")
    public String toPolicyTimeWindowString(PolicyTimeWindow value) {
        if (value == null) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            if (value.getType() == null) {
                root.putNull("type");
            } else {
                root.put("type", value.getType().getCode());
            }
            if (value.getStartAt() == null) {
                root.putNull("startAt");
            } else {
                root.put("startAt", value.getStartAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            if (value.getEndAt() == null) {
                root.putNull("endAt");
            } else {
                root.put("endAt", value.getEndAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PolicyTimeWindow JSON 序列化失败", e);
        }
    }

    private LocalDateTime parseNullableLocalDateTime(String value) {
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return LocalDateTime.parse(value);
    }
}
