package com.wewins.fota.domain.device.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备多部分版本集合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceVersionParts {

    /**
     * 版本部分映射
     */
    @JsonProperty("parts")
    @Builder.Default
    private Map<String, DeviceVersionPart> parts = new HashMap<>();

    /**
     * 主要部分名称（默认 main）
     */
    @JsonProperty("primaryPart")
    @Builder.Default
    private String primaryPart = "main";

    /**
     * 获取主要版本 ID
     */
    @JsonIgnore
    public Long getPrimaryVersionId() {
        DeviceVersionPart part = parts.get(primaryPart);
        return part != null ? part.getVersionId() : null;
    }

    /**
     * 获取主要版本号
     */
    @JsonIgnore
    public String getPrimaryVersion() {
        DeviceVersionPart part = parts.get(primaryPart);
        return part != null ? part.getVersion() : null;
    }

    @JsonIgnore
    public Set<Long> getVersionIds() {
        return parts.values().stream()
                .map(DeviceVersionPart::getVersionId).collect(Collectors.toSet());
    }

    public boolean hasVersion() {
        return parts != null && !parts.isEmpty();
    }

    /**
     * 更新某个部分的版本
     */
    public void updatePart(String partName, Long versionId, LocalDateTime updatedAt) {
        addPart(partName, DeviceVersionPart.builder()
                .versionId(versionId)
                .updatedAt(updatedAt)
                .build());
    }

    public void addPart(String partName, DeviceVersionPart part) {
        parts.put(partName, part);
    }

    /**
     * 判断是否与请求版本匹配
     */
    public boolean matchesPartVersion(String partName, String version) {
        DeviceVersionPart part = parts.get(partName);
        return part != null && version != null && version.equals(part.getVersion());
    }

    /**
     * 判断是否与请求版本 ID 匹配
     */
    public boolean matchesPartVersionId(String partName, Long versionId) {
        DeviceVersionPart part = parts.get(partName);
        return part != null && versionId != null && versionId.equals(part.getVersionId());
    }

    public boolean matchAny(Long versionId) {
        return !parts.isEmpty() &&
                versionId != null &&
                parts.values().stream().anyMatch(
                        p -> p.getVersionId().equals(versionId)
                );
    }

    public boolean hasVersion(String partName) {
        return parts.containsKey(partName);
    }
}
