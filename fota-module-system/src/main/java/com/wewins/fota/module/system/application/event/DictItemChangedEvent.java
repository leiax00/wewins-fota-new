package com.wewins.fota.module.system.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

/**
 * 字典项变更事件。
 * <p>
 * 只承载字典项发布后需要消费的最小信息，供其他模块在事务提交后监听。
 * </p>
 */
@Value
@Builder
public class DictItemChangedEvent {

    Action action;
    Long itemId;
    String dictTypeCode;
    String beforeValue;
    JsonNode beforeExtra;
    Long beforeUpdatedBy;
    String afterValue;
    JsonNode afterExtra;
    Long afterUpdatedBy;

    public enum Action {
        CREATED,
        UPDATED,
        DELETED
    }
}
