package com.wewins.fota.application.audit.dto;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wewins.fota.common.dto.BaseQueryDTO;
import com.wewins.fota.database.wrapper.LambdaQueryWrapperX;
import com.wewins.fota.domain.audit.model.entity.OperationLog;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogPageReqDTO extends BaseQueryDTO {

    private String operatorKeyword;
    private String moduleCode;
    private String resourceCode;
    private String operationType;
    private String targetId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime[] timeRange;

    private static final Map<String, SFunction<OperationLog, ?>> FIELD_MAP = new HashMap<>();

    static {
        FIELD_MAP.put("occurredAt", OperationLog::getOccurredAt);
        FIELD_MAP.put("moduleCode", OperationLog::getModuleCode);
        FIELD_MAP.put("resourceCode", OperationLog::getResourceCode);
        FIELD_MAP.put("operationType", OperationLog::getOperationType);
        FIELD_MAP.put("operatorUsername", OperationLog::getOperatorUsername);
        FIELD_MAP.put("operatorDisplayName", OperationLog::getOperatorDisplayName);
        FIELD_MAP.put("targetId", OperationLog::getTargetId);
    }

    public LocalDateTime getTimeRangeStart() {
        return timeRange != null && timeRange.length > 0 ? timeRange[0] : null;
    }

    public LocalDateTime getTimeRangeEnd() {
        return timeRange != null && timeRange.length > 1 ? timeRange[1] : null;
    }

    public LambdaQueryWrapperX<OperationLog> toWrapper() {
        LambdaQueryWrapperX<OperationLog> wrapper = new LambdaQueryWrapperX<>(OperationLog.class)
                .eqIfPresent(OperationLog::getModuleCode, moduleCode)
                .eqIfPresent(OperationLog::getResourceCode, resourceCode)
                .eqIfPresent(OperationLog::getOperationType, operationType)
                .eqIfPresent(OperationLog::getTargetId, targetId)
                .betweenIfPresent(OperationLog::getOccurredAt, getTimeRangeStart(), getTimeRangeEnd());

        if (StringUtils.hasText(operatorKeyword)) {
            wrapper.and(w -> w.like(OperationLog::getOperatorUsername, operatorKeyword)
                    .or()
                    .like(OperationLog::getOperatorDisplayName, operatorKeyword));
        }

        if (getSortingFields() == null || getSortingFields().isEmpty()) {
            wrapper.orderByDesc(OperationLog::getOccurredAt);
        } else {
            wrapper.applySortingIfPresent(getSortingFields(), FIELD_MAP);
        }
        return wrapper;
    }
}
