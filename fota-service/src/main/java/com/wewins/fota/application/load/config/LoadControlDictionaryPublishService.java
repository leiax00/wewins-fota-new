    package com.wewins.fota.application.load.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.sentinel.dto.SentinelDegradeRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelFlowRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.infra.sentinel.config.SentinelRuleManager;
import com.wewins.fota.module.system.application.DictItemAppService;
import com.wewins.fota.module.system.application.event.DictItemChangedEvent;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.domain.repository.dict.DictItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadControlDictionaryPublishService {

    static final String TYPE_CONTROL_PARAMETER = "load_control.control_parameter";
    static final String TYPE_SCORING_INSTANCE = "load_control.scoring.instance";
    static final String TYPE_SCORING_HOST = "load_control.scoring.host";
    static final String TYPE_SCORING_REGION = "load_control.scoring.region";
    static final String TYPE_SENTINEL_RULE = "load_control.sentinel.rule";
    static final String TYPE_META = "load_control.meta";
    static final String VALUE_PUBLISH_SWITCH = "snapshot.publish";

    private final DictItemAppService dictItemAppService;
    private final DictItemRepository dictItemRepository;
    private final ObjectMapper objectMapper;
    private final SentinelRuleManager sentinelRuleManager;
    private final LoadControlRuntimeConfigService runtimeConfigService;

    public boolean shouldPublish(DictItemChangedEvent event) {
        if (event == null || !TYPE_META.equals(event.getDictTypeCode())) {
            return false;
        }
        if (!VALUE_PUBLISH_SWITCH.equals(event.getAfterValue())) {
            return false;
        }
        return !extractPublishEnabled(event.getBeforeExtra()) && extractPublishEnabled(event.getAfterExtra());
    }

    public void publishFromDictionary(DictItemChangedEvent event) {
        publishSnapshot(resolveUpdatedBy(event), true, event.getItemId());
    }

    public void publishFromDictionary(String updatedBy) {
        publishSnapshot(updatedBy == null || updatedBy.isBlank() ? "startup-initializer" : updatedBy, false, null);
    }

    private void publishSnapshot(String updatedBy, boolean resetPublishSwitch, Long publishSwitchItemId) {
        LoadControlRuntimeConfig snapshot = LoadControlRuntimeConfig.builder()
                .version(runtimeConfigService.nextVersion())
                .updatedAt(Instant.now())
                .updatedBy(updatedBy)
                .control(resolveControlParameter())
                .scoring(LoadScoringConfig.builder()
                        .instanceMetrics(loadMetrics(TYPE_SCORING_INSTANCE))
                        .hostMetrics(loadMetrics(TYPE_SCORING_HOST))
                        .regionMetrics(loadMetrics(TYPE_SCORING_REGION))
                        .build())
                .sentinel(resolveSentinelRules())
                .build();

        runtimeConfigService.publish(snapshot);
        sentinelRuleManager.loadRules();
        sentinelRuleManager.markLoadedVersion(snapshot.getVersion());
        runtimeConfigService.evictLocalCache();
        if (resetPublishSwitch) {
            resetPublishSwitch(publishSwitchItemId);
        }
        log.info("Published load-control snapshot from dictionary: version={}", snapshot.getVersion());
    }

    private ControlParameter resolveControlParameter() {
        return dictItemAppService.listItemsByTypeCode(TYPE_CONTROL_PARAMETER).stream()
                .filter(this::isActive)
                .findFirst()
                .map(this::toControlParameter)
                .orElseGet(ControlParameter::createGlobalDefault);
    }

    private List<LoadScoringMetricConfig> loadMetrics(String typeCode) {
        return dictItemAppService.listItemsByTypeCode(typeCode).stream()
                .filter(this::isActive)
                .map(item -> convert(item.getExtra(), LoadScoringMetricConfig.class))
                .filter(Objects::nonNull)
                .toList();
    }

    private SentinelRulesDTO resolveSentinelRules() {
        List<DictItem> items = dictItemAppService.listItemsByTypeCode(TYPE_SENTINEL_RULE).stream()
                .filter(this::isActive)
                .toList();

        List<SentinelFlowRuleDTO> flowRules = items.stream()
                .filter(item -> isKind(item.getExtra(), "sentinel_flow_rule"))
                .map(item -> convert(item.getExtra(), SentinelFlowRuleDTO.class))
                .filter(Objects::nonNull)
                .filter(rule -> rule.getResource() != null && !rule.getResource().isBlank())
                .filter(rule -> rule.getGrade() != null)
                .filter(rule -> rule.getControlBehavior() != null)
                .toList();

        List<SentinelDegradeRuleDTO> degradeRules = items.stream()
                .filter(item -> isKind(item.getExtra(), "sentinel_degrade_rule"))
                .map(item -> convert(item.getExtra(), SentinelDegradeRuleDTO.class))
                .filter(Objects::nonNull)
                .filter(rule -> rule.getResource() != null && !rule.getResource().isBlank())
                .filter(rule -> rule.getGrade() != null)
                .filter(rule -> rule.getTimeWindow() > 0)
                .toList();

        return SentinelRulesDTO.builder()
                .flowRules(flowRules)
                .degradeRules(degradeRules)
                .build();
    }

    private ControlParameter toControlParameter(DictItem item) {
        ControlParameter parameter = convert(item.getExtra(), ControlParameter.class);
        if (parameter == null) {
            return ControlParameter.createGlobalDefault();
        }
        parameter.setUpdatedAt(Instant.now());
        parameter.setUpdatedBy("dict-publisher");
        return parameter;
    }

    private boolean isActive(DictItem item) {
        return item != null && "active".equalsIgnoreCase(item.getStatus());
    }

    private <T> T convert(JsonNode source, Class<T> type) {
        if (source == null || source.isNull()) {
            return null;
        }
        try {
            return objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .treeToValue(source, type);
        } catch (Exception e) {
            log.warn("Failed to convert dictionary item extra to {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private boolean isKind(JsonNode extra, String expectedKind) {
        return extra != null && expectedKind.equals(extra.path("kind").asText(null));
    }

    private boolean extractPublishEnabled(JsonNode extra) {
        return extra != null && extra.path("enabled").asBoolean(false);
    }

    private String resolveUpdatedBy(DictItemChangedEvent event) {
        Long operatorId = event.getAfterUpdatedBy() != null ? event.getAfterUpdatedBy() : event.getBeforeUpdatedBy();
        return operatorId == null ? "dict-publisher" : String.valueOf(operatorId);
    }

    private void resetPublishSwitch(Long itemId) {
        if (itemId == null) {
            return;
        }
        DictItem item = dictItemRepository.findById(itemId);
        if (item == null || item.getExtra() == null || !(item.getExtra() instanceof ObjectNode objectNode)) {
            return;
        }
        objectNode.put("enabled", false);
        dictItemRepository.updateById(item);
    }
}
