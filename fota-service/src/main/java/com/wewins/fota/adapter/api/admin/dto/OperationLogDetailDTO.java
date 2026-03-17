package com.wewins.fota.adapter.api.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OperationLogDetailDTO {

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
    private String requestMethod;
    private String requestPath;
    private JsonNode requestQuery;
    private JsonNode requestBody;
    private String clientIp;
    private String userAgent;
    private LocalDateTime occurredAt;
}
