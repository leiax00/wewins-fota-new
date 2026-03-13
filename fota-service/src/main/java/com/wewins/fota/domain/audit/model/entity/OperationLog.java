package com.wewins.fota.domain.audit.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.database.entity.AutoIdEntity;
import com.wewins.fota.database.handler.JsonNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_operation_logs", autoResultMap = true)
public class OperationLog extends AutoIdEntity {

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

    @TableField(typeHandler = JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode requestQuery;

    @TableField(typeHandler = JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode requestBody;

    private String clientIp;
    private String userAgent;
    private LocalDateTime occurredAt;
}
