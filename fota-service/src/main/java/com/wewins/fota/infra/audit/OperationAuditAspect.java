package com.wewins.fota.infra.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.common.util.HttpUtils;
import com.wewins.fota.domain.audit.model.entity.OperationLog;
import com.wewins.fota.security.jwt.SysUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationAuditAspect {

    private static final Set<String> QUERY_RESOURCE_SEGMENTS = Set.of("preview", "estimate", "parse");

    private final OperationAuditPayloadSanitizer payloadSanitizer;
    private final OperationAuditRecorder operationAuditRecorder;

    @Around("("
            + "within(com.wewins.fota.adapter.api.admin..*)"
            + " || within(com.wewins.fota.module.system.adapter.api.admin..*)"
            + ") && execution(public * *(..))")
    public Object auditOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        AuditContext context = buildContext(request, joinPoint);
        if (context == null) {
            return joinPoint.proceed();
        }

        Object result = null;
        Throwable thrown = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            thrown = ex;
            throw ex;
        } finally {
            OperationLog operationLog = buildOperationLog(context, result, thrown);
            if (operationLog != null) {
                operationAuditRecorder.recordQuietly(operationLog);
            }
        }
    }

    AuditContext buildContext(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        if (!shouldAudit(request)) {
            return null;
        }

        String path = normalize(request.getRequestURI());
        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        String operationType = resolveOperationType(request.getMethod(), methodName, path);
        if (operationType == null) {
            return null;
        }

        AuditContext context = new AuditContext();
        context.request = request;
        context.path = path;
        context.requestMethod = normalize(request.getMethod());
        context.moduleCode = resolveModuleCode(path);
        context.resourceCode = resolveResourceCode(path);
        context.actionCode = resolveActionCode(methodName);
        context.operationType = operationType;
        context.targetId = resolveTargetId(request);
        context.requestQuery = payloadSanitizer.sanitizeQuery(extractRequestQuery(request));
        context.requestBody = payloadSanitizer.sanitizeRequestBody(joinPoint.getArgs());
        context.targetName = resolveTargetName(context.requestBody, null);
        context.operator = resolveOperator();
        return context;
    }

    OperationLog buildOperationLog(AuditContext context, Object result, Throwable thrown) {
        if (context == null) {
            return null;
        }

        String targetId = context.targetId;
        if (targetId == null) {
            targetId = extractTargetId(result);
        }
        String targetName = context.targetName;
        if (targetName == null) {
            targetName = resolveTargetName(context.requestBody, result);
        }

        OperationLog operationLog = new OperationLog();
        operationLog.setModuleCode(context.moduleCode);
        operationLog.setResourceCode(context.resourceCode);
        operationLog.setActionCode(context.actionCode);
        operationLog.setOperationType(context.operationType);
        operationLog.setTargetId(targetId);
        operationLog.setTargetName(targetName);
        operationLog.setRequestMethod(context.requestMethod);
        operationLog.setRequestPath(context.path);
        operationLog.setRequestQuery(context.requestQuery);
        operationLog.setRequestBody(context.requestBody);
        operationLog.setClientIp(HttpUtils.extractClientIp(context.request));
        operationLog.setUserAgent(normalize(context.request.getHeader("User-Agent")));
        operationLog.setOccurredAt(LocalDateTime.now());

        if (context.operator != null) {
            operationLog.setOperatorId(context.operator.id());
            operationLog.setOperatorUsername(context.operator.username());
            operationLog.setOperatorDisplayName(context.operator.displayName());
        } else {
            operationLog.setOperatorUsername("anonymous");
            operationLog.setOperatorDisplayName("anonymous");
        }

        if (thrown != null && log.isDebugEnabled()) {
            log.debug("操作日志记录了异常请求: actionCode={}, message={}", context.actionCode, thrown.getMessage());
        }
        return operationLog;
    }

    boolean shouldAudit(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String method = normalize(request.getMethod());
        if (!List.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            return false;
        }
        String uri = normalize(request.getRequestURI());
        if (uri.isBlank() || uri.startsWith("/api/sys/auth")) {
            return false;
        }
        if (!(uri.startsWith("/api/admin/") || uri.startsWith("/api/sys/"))) {
            return false;
        }
        return QUERY_RESOURCE_SEGMENTS.stream().noneMatch(segment -> uri.contains("/" + segment));
    }

    String resolveOperationType(String requestMethod, String methodName, String path) {
        String normalizedMethodName = normalize(methodName).toLowerCase(Locale.ROOT);
        String normalizedPath = normalize(path).toLowerCase(Locale.ROOT);

        if (normalizedMethodName.startsWith("login")
                || normalizedMethodName.startsWith("logout")
                || normalizedMethodName.startsWith("preview")
                || normalizedMethodName.startsWith("estimate")
                || normalizedMethodName.startsWith("parse")) {
            return null;
        }
        if (normalizedMethodName.contains("import") || normalizedPath.contains("/import/execute")) {
            return "IMPORT";
        }
        if (normalizedMethodName.contains("export")) {
            return "EXPORT";
        }
        if (normalizedMethodName.startsWith("assign")) {
            return "ASSIGN";
        }
        if (normalizedMethodName.startsWith("execute")
                || normalizedMethodName.startsWith("publish")
                || normalizedMethodName.startsWith("release")
                || normalizedMethodName.startsWith("refresh")
                || normalizedMethodName.startsWith("start")) {
            return "EXECUTE";
        }
        if (normalizedMethodName.startsWith("delete")
                || normalizedMethodName.startsWith("remove")
                || normalizedMethodName.startsWith("cancel")
                || normalizedMethodName.startsWith("evict")
                || normalizedMethodName.startsWith("clear")
                || "DELETE".equalsIgnoreCase(requestMethod)) {
            return "DELETE";
        }
        if (normalizedMethodName.startsWith("create")
                || normalizedMethodName.startsWith("add")
                || normalizedMethodName.startsWith("upload")) {
            return "CREATE";
        }
        if (normalizedMethodName.startsWith("update")
                || normalizedMethodName.startsWith("set")
                || normalizedMethodName.startsWith("attach")
                || "PUT".equalsIgnoreCase(requestMethod)
                || "PATCH".equalsIgnoreCase(requestMethod)) {
            return "UPDATE";
        }
        return "POST".equalsIgnoreCase(requestMethod) ? "CREATE" : null;
    }

    String resolveModuleCode(String path) {
        String[] segments = path.split("/");
        if (segments.length < 4) {
            return "unknown";
        }
        if ("sys".equals(segments[2])) {
            return "sys";
        }
        return switch (segments[3]) {
            case "cache", "control", "sentinel", "monitor" -> "monitor";
            default -> "fota";
        };
    }

    String resolveResourceCode(String path) {
        String[] segments = path.split("/");
        if (segments.length < 4) {
            return "unknown";
        }
        return switch (segments[3]) {
            case "users" -> "user";
            case "roles" -> "role";
            case "permissions" -> "permission";
            case "dict-types" -> "dict_type";
            case "dict-items" -> "dict_item";
            case "products" -> "product";
            case "devices" -> "device";
            case "device-import-batches" -> "device_import_batch";
            case "firmware-versions", "firmware-uploads" -> "firmware";
            case "policies" -> "policy";
            case "cache" -> "cache";
            case "control" -> "control";
            case "sentinel" -> "sentinel";
            default -> segments[3].replace('-', '_');
        };
    }

    String resolveActionCode(String methodName) {
        String value = normalize(methodName);
        if (value.isBlank()) {
            return "unknown_action";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }

    String resolveTargetId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> variables) {
            for (String key : List.of("id", "userId", "productId", "sessionId")) {
                Object value = variables.get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        return null;
    }

    String extractTargetId(Object value) {
        Object extracted = extractCandidateValue(unwrapPayload(value), List.of("getId", "getBatchId", "getSessionId"));
        return extracted == null ? null : String.valueOf(extracted);
    }

    String resolveTargetName(JsonNode requestBody, Object result) {
        String fromRequest = extractCandidateText(requestBody, List.of(
                "name", "username", "displayName", "code", "productModel", "batchName"
        ));
        if (fromRequest != null) {
            return fromRequest;
        }

        Object extracted = extractCandidateValue(unwrapPayload(result), List.of(
                "getName", "getUsername", "getDisplayName", "getCode", "getBatchName", "getProductName"
        ));
        return extracted == null ? null : String.valueOf(extracted);
    }

    OperatorContext resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SysUserDetails sysUserDetails) {
            return new OperatorContext(
                    sysUserDetails.getUserId(),
                    sysUserDetails.getUsername(),
                    sysUserDetails.getDisplayName()
            );
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return null;
        }
        return new OperatorContext(null, username, username);
    }

    Map<String, Object> extractRequestQuery(HttpServletRequest request) {
        Map<String, Object> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values == null || values.length == 0) {
                return;
            }
            query.put(key, values.length == 1 ? values[0] : List.of(values));
        });
        return query;
    }

    private String extractCandidateText(JsonNode node, List<String> fieldNames) {
        return payloadSanitizer.extractCandidateText(node, fieldNames);
    }

    private Object extractCandidateValue(Object value, List<String> getterNames) {
        if (value == null) {
            return null;
        }
        for (String getterName : getterNames) {
            try {
                Method method = value.getClass().getMethod(getterName);
                Object candidate = method.invoke(value);
                if (candidate != null && !String.valueOf(candidate).isBlank()) {
                    return candidate;
                }
            } catch (ReflectiveOperationException ignored) {
                // continue
            }
        }
        return null;
    }

    private Object unwrapPayload(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod("getData");
            return method.invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return value;
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static final class AuditContext {
        private HttpServletRequest request;
        private String moduleCode;
        private String resourceCode;
        private String actionCode;
        private String operationType;
        private String targetId;
        private String targetName;
        private String requestMethod;
        private String path;
        private JsonNode requestQuery;
        private JsonNode requestBody;
        private OperatorContext operator;
    }

    record OperatorContext(Long id, String username, String displayName) {
    }
}
