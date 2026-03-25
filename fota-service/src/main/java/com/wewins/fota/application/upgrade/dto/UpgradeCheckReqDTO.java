package com.wewins.fota.application.upgrade.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.common.enums.CheckMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

/**
 * 设备升级检查请求 DTO
 * <p>
 * 用于 GET/POST /v1/upgrade/check 接口的请求参数
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Data
public class UpgradeCheckReqDTO {

    private static final ObjectMapper EXT_TAGS_MAPPER = new ObjectMapper();
    private static final Set<String> CORE_FIELDS = Set.of(
            "product", "imei", "version", "auto", "lang", "tag", "dev", "extTags"
    );

    /**
     * 产品名称（必选）
     */
    @NotBlank(message = "product 参数不能为空")
    private String product;

    /**
     * 设备 IMEI（必选）
     */
    @NotBlank(message = "imei 参数不能为空")
    private String imei;

    /**
     * 当前固件版本（必选）
     */
    @NotBlank(message = "version 参数不能为空")
    private String version;

    /**
     * 检查模式：0=手动, 1=自动（默认自动）
     */
    private Integer auto = 1;

    /**
     * 获取检查模式枚举
     * <p>
     * 默认返回自动检查模式
     * </p>
     */
    public CheckMode getCheckMode() {
        if (auto == null || auto == 1) {
            return CheckMode.AUTO;
        }
        return CheckMode.MANUAL;
    }

    /**
     * 语言代码
     */
    private String lang;

    /**
     * 设备标签
     */
    private String tag;

    /**
     * 是否开发环境（1: 是, 不填或0: 否，默认 0）
     */
    private Integer dev = 0;

    /**
     * 扩展标签（JSON 对象，仅 POST）
     */
    private JsonNode extTags;

    /**
     * 将未知顶层 JSON 字段自动并入扩展标签，避免每次新增参数都修改 DTO 结构。
     */
    @JsonAnySetter
    public void captureExtensionField(String name, Object value) {
        if (CORE_FIELDS.contains(name) || value == null) {
            return;
        }

        ensureExtTagsObject().set(name, EXT_TAGS_MAPPER.valueToTree(value));
    }

    /**
     * 设置单个扩展标签。
     */
    public void putExtTag(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        ensureExtTagsObject().set(key, EXT_TAGS_MAPPER.valueToTree(value));
    }

    /**
     * 读取扩展标签文本值。
     */
    public String getExtTagValue(String key) {
        if (!(extTags instanceof ObjectNode objectNode) || key == null || key.isBlank()) {
            return null;
        }

        JsonNode value = objectNode.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    /**
     * 常用扩展参数访问器，便于业务后续逐步接入。
     */
    public String getHardwareVersion() {
        return getExtTagValue("hw");
    }

    private ObjectNode ensureExtTagsObject() {
        if (!(extTags instanceof ObjectNode objectNode)) {
            ObjectNode objectNode = EXT_TAGS_MAPPER.createObjectNode();
            extTags = objectNode;
            return objectNode;
        }
        return objectNode;
    }
}
