package com.wewins.fota.infra.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.domain.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OperationAuditAspectTest {

    private OperationAuditAspect aspect;
    private OperationAuditPayloadSanitizer payloadSanitizer;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        OperationAuditRecorder recorder = mock(OperationAuditRecorder.class);
        payloadSanitizer = new OperationAuditPayloadSanitizer(new ObjectMapper());
        aspect = new OperationAuditAspect(payloadSanitizer, recorder);
    }

    @Test
    void shouldAuditOnlyAdminMutationRequests() {
        MockHttpServletRequest createRequest = new MockHttpServletRequest("POST", "/api/sys/users");
        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/sys/auth/login");
        MockHttpServletRequest queryRequest = new MockHttpServletRequest("GET", "/api/admin/products");
        MockHttpServletRequest previewRequest = new MockHttpServletRequest("POST", "/api/admin/cache/evict/preview");

        assertTrue(aspect.shouldAudit(createRequest));
        assertFalse(aspect.shouldAudit(loginRequest));
        assertFalse(aspect.shouldAudit(queryRequest));
        assertFalse(aspect.shouldAudit(previewRequest));
    }

    @Test
    void shouldResolveOperationTypesForCommonAdminActions() {
        assertEquals("CREATE", aspect.resolveOperationType("POST", "createUser", "/api/sys/users"));
        assertEquals("ASSIGN", aspect.resolveOperationType("POST", "assignRoles", "/api/sys/users/1/roles"));
        assertEquals("DELETE", aspect.resolveOperationType("DELETE", "deleteUser", "/api/sys/users/1"));
        assertEquals("IMPORT", aspect.resolveOperationType("POST", "executeImport", "/api/admin/devices/import/execute"));
        assertEquals("EXECUTE", aspect.resolveOperationType("POST", "executeBatchOperation", "/api/admin/devices/batch/execute"));
        assertNull(aspect.resolveOperationType("POST", "previewEvict", "/api/admin/cache/evict/preview"));
    }

    @Test
    void shouldMaskSensitiveFieldsInRequestBody() {
        JsonNode sanitizedNode = payloadSanitizer.sanitizeRequestBody(new Object[]{Map.of(
                "username", "alice",
                "passwordHash", "secret",
                "profile", Map.of("token", "abc", "phone", "13800000000"),
                "roleIds", List.of(1L, 2L)
        )});

        Map<String, Object> sanitized = new ObjectMapper().convertValue(sanitizedNode, Map.class);

        assertEquals("alice", sanitized.get("username"));
        assertEquals("***", sanitized.get("passwordHash"));
        assertEquals(List.of(1L, 2L), sanitized.get("roleIds"));

        Map<String, Object> profile = (Map<String, Object>) sanitized.get("profile");
        assertEquals("***", profile.get("token"));
        assertEquals("13800000000", profile.get("phone"));
    }

    @Test
    void shouldResolveTargetIdFromPathVariablesFirst() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/sys/users/10");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "9"));

        assertEquals("9", aspect.resolveTargetId(request));
    }

    @Test
    void shouldExtractTargetNameFromRequestBodyFirst() {
        JsonNode requestBody = new ObjectMapper().valueToTree(Map.of(
                "username", "operator",
                "displayName", "管理员"
        ));

        assertEquals("operator", aspect.resolveTargetName(requestBody, null));
    }

    @Test
    void shouldResolveOperatorModuleAndResourceFromPath() {
        assertEquals("sys", aspect.resolveModuleCode("/api/sys/users"));
        assertEquals("fota", aspect.resolveModuleCode("/api/admin/devices"));
        assertEquals("monitor", aspect.resolveModuleCode("/api/admin/cache"));
        assertEquals("device", aspect.resolveResourceCode("/api/admin/devices/import/execute"));
        assertEquals("dict_type", aspect.resolveResourceCode("/api/sys/dict-types"));
    }

    @Test
    void shouldResolveActionCodeFromMethodName() {
        assertEquals("create_user", aspect.resolveActionCode("createUser"));
        assertEquals("assign_roles", aspect.resolveActionCode("assignRoles"));
        assertEquals("execute_batch_operation", aspect.resolveActionCode("executeBatchOperation"));
    }

    @Test
    void shouldResolveTargetNameFromResponseWhenRequestBodyMissing() {
        User user = User.builder()
                .username("demo")
                .displayName("Demo User")
                .build();

        assertEquals("demo", aspect.resolveTargetName(null, user));
    }
}
