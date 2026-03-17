package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OperationLogRespDTO {

    private Long id;
    private String moduleCode;
    private String resourceCode;
    private String actionCode;
    private String operationType;
    private String targetId;
    private String targetName;
    private Long operatorId;
    private String operatorUsername;
    private String operatorDisplayName;
    private String clientIp;
    private LocalDateTime occurredAt;
}
