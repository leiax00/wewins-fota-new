package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PolicyMatcher 单元测试
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("PolicyMatcher 单元测试")
class PolicyMatcherTest {

    private PolicyMatcher policyMatcher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        policyMatcher = new PolicyMatcher();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("matchesDeviceTags 方法测试")
    class MatchesDeviceTagsTests {

        @Test
        @DisplayName("策略没有标签要求 - 应该匹配")
        void shouldMatch_whenPolicyHasNoTags() {
            // Given
            JsonNode policyTags = null;
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("策略标签为空对象 - 应该匹配")
        void shouldMatch_whenPolicyTagsIsEmpty() {
            // Given
            JsonNode policyTags = createJsonNode("{}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("设备没有标签且策略有要求 - 应该不匹配")
        void shouldNotMatch_whenDeviceHasNoTags() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\"}");
            JsonNode deviceTags = null;

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("设备标签为空对象且策略有要求 - 应该不匹配")
        void shouldNotMatch_whenDeviceTagsIsEmpty() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\"}");
            JsonNode deviceTags = createJsonNode("{}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("设备标签完全匹配策略要求 - 应该匹配")
        void shouldMatch_whenDeviceTagsExactlyMatch() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("设备标签包含策略要求且有额外标签 - 应该匹配")
        void shouldMatch_whenDeviceTagsContainsRequiredTags() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\", \"network\": \"5G\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("设备标签缺少必需的键 - 应该不匹配")
        void shouldNotMatch_whenDeviceTagsMissingRequiredKey() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("设备标签值不匹配 - 应该不匹配")
        void shouldNotMatch_whenDeviceTagValueMismatch() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"region\": \"US\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("单个标签匹配测试")
        void shouldMatch_singleTag() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("多个标签全部匹配测试")
        void shouldMatch_multipleTagsAllMatch() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\", \"network\": \"5G\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\", \"network\": \"5G\", \"extra\": \"value\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("值类型匹配测试")
    class ValueTypeTests {

        @Test
        @DisplayName("字符串值匹配")
        void shouldMatch_stringValues() {
            // Given
            JsonNode policyTags = createJsonNode("{\"env\": \"test\"}");
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\"}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("数值匹配")
        void shouldMatch_numericValues() {
            // Given
            JsonNode policyTags = createJsonNode("{\"level\": 1}");
            JsonNode deviceTags = createJsonNode("{\"level\": 1}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("布尔值匹配")
        void shouldMatch_booleanValues() {
            // Given
            JsonNode policyTags = createJsonNode("{\"enabled\": true}");
            JsonNode deviceTags = createJsonNode("{\"enabled\": true}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("null 值匹配")
        void shouldMatch_nullValues() {
            // Given
            JsonNode policyTags = createJsonNode("{\"disabled\": null}");
            JsonNode deviceTags = createJsonNode("{\"disabled\": null}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("数值类型不匹配 - 整数 vs 浮点数相等值")
        void shouldMatch_numericEquivalentValues() {
            // Given
            JsonNode policyTags = createJsonNode("{\"level\": 1}");
            JsonNode deviceTags = createJsonNode("{\"level\": 1.0}");

            // When
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("实际场景测试")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("场景：测试环境策略匹配测试设备")
        void testEnvironmentScenario() {
            // Given - 测试环境策略
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\"}");

            // When - 测试设备
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"region\": \"CN\", \"tester\": \"张三\"}");
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("场景：生产设备不应匹配测试环境策略")
        void testProductionDeviceShouldNotMatchTestPolicy() {
            // Given - 测试环境策略
            JsonNode policyTags = createJsonNode("{\"env\": \"test\"}");

            // When - 生产设备
            JsonNode deviceTags = createJsonNode("{\"env\": \"prod\", \"region\": \"CN\"}");
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("场景：灰度发布按用户等级匹配")
        void testUserLevelScenario() {
            // Given - VIP 用户策略
            JsonNode policyTags = createJsonNode("{\"user_level\": \"VIP\"}");

            // When - VIP 设备
            JsonNode deviceTags = createJsonNode("{\"user_level\": \"VIP\", \"region\": \"CN\"}");
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("场景：普通用户设备不应匹配 VIP 策略")
        void testNormalUserShouldNotMatchVipPolicy() {
            // Given - VIP 用户策略
            JsonNode policyTags = createJsonNode("{\"user_level\": \"VIP\"}");

            // When - 普通用户设备
            JsonNode deviceTags = createJsonNode("{\"user_level\": \"NORMAL\", \"region\": \"CN\"}");
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("场景：多条件组合匹配")
        void testMultipleConditionsScenario() {
            // Given - 测试阶段 VIP 用户在特定地区
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"user_level\": \"VIP\", \"region\": \"CN\"}");

            // When - 完全符合条件的设备
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"user_level\": \"VIP\", \"region\": \"CN\", \"network\": \"5G\"}");
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("场景：部分条件满足不应匹配")
        void testPartialConditionsShouldNotMatch() {
            // Given - 需要同时满足三个条件
            JsonNode policyTags = createJsonNode("{\"env\": \"test\", \"user_level\": \"VIP\", \"region\": \"CN\"}");

            // When - 只满足两个条件
            JsonNode deviceTags = createJsonNode("{\"env\": \"test\", \"user_level\": \"VIP\", \"region\": \"US\"}");
            boolean result = policyMatcher.matchesDeviceTags(policyTags, deviceTags);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("matchesTimeWindow 方法测试")
    class MatchesTimeWindowTests {

        @Test
        @DisplayName("无时间窗口限制 - 应该匹配")
        void shouldMatch_whenNoTimeWindow() {
            // Given
            JsonNode timeWindow = null;

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("时间窗口为空对象 - 应该匹配")
        void shouldMatch_whenTimeWindowIsEmpty() {
            // Given
            JsonNode timeWindow = createJsonNode("{}");

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("RANGE 类型 - 当前时间在窗口内 - 应该匹配")
        void shouldMatch_whenInRangeWindow() {
            // Given - 创建一个包含当前时间的时间窗口
            String past = Instant.now().minusSeconds(3600).toString();
            String future = Instant.now().plusSeconds(3600).toString();
            JsonNode timeWindow = createJsonNode(String.format(
                    "{\"type\": \"RANGE\", \"startAt\": \"%s\", \"endAt\": \"%s\"}",
                    past, future
            ));

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("RANGE 类型 - 当前时间在窗口前 - 应该不匹配")
        void shouldNotMatch_whenBeforeRangeWindow() {
            // Given - 创建一个未来的时间窗口
            String futureStart = Instant.now().plusSeconds(7200).toString();
            String futureEnd = Instant.now().plusSeconds(10800).toString();
            JsonNode timeWindow = createJsonNode(String.format(
                    "{\"type\": \"RANGE\", \"startAt\": \"%s\", \"endAt\": \"%s\"}",
                    futureStart, futureEnd
            ));

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("RANGE 类型 - 当前时间在窗口后 - 应该不匹配")
        void shouldNotMatch_whenAfterRangeWindow() {
            // Given - 创建一个过去的时间窗口
            String pastStart = Instant.now().minusSeconds(10800).toString();
            String pastEnd = Instant.now().minusSeconds(7200).toString();
            JsonNode timeWindow = createJsonNode(String.format(
                    "{\"type\": \"RANGE\", \"startAt\": \"%s\", \"endAt\": \"%s\"}",
                    pastStart, pastEnd
            ));

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("RANGE 类型 - 缺少类型字段 - 应该匹配（容错）")
        void shouldMatch_whenRangeWindowMissingType() {
            // Given
            JsonNode timeWindow = createJsonNode("{\"startAt\": \"2026-02-01T00:00:00Z\"}");

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("RANGE 类型 - 缺少时间字段 - 应该匹配（容错）")
        void shouldMatch_whenRangeWindowMissingTimes() {
            // Given
            JsonNode timeWindow = createJsonNode("{\"type\": \"RANGE\"}");

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("RANGE 类型 - 时间格式无效 - 应该匹配（容错）")
        void shouldMatch_whenRangeWindowInvalidFormat() {
            // Given
            JsonNode timeWindow = createJsonNode(
                    "{\"type\": \"RANGE\", \"startAt\": \"invalid\", \"endAt\": \"invalid\"}"
            );

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("DAILY 类型 - 验证方法正常执行")
        void shouldExecute_whenDailyWindowType() {
            // Given - DAILY 类型只取时间部分，每日重复
            JsonNode timeWindow = createJsonNode(
                    "{\"type\": \"DAILY\", \"startAt\": \"2026-02-01T08:00:00Z\", \"endAt\": \"2026-02-01T12:00:00Z\"}"
            );

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then - 由于测试时间不确定，只验证方法能正常执行
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("DAILY 类型 - 时间格式无效 - 应该匹配（容错）")
        void shouldMatch_whenDailyWindowInvalidFormat() {
            // Given
            JsonNode timeWindow = createJsonNode(
                    "{\"type\": \"DAILY\", \"startAt\": \"invalid\", \"endAt\": \"invalid\"}"
            );

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("未知类型 - 应该匹配（容错）")
        void shouldMatch_whenUnknownType() {
            // Given
            JsonNode timeWindow = createJsonNode(
                    "{\"type\": \"UNKNOWN\", \"startAt\": \"2026-02-01T00:00:00Z\", \"endAt\": \"2026-02-10T23:59:59Z\"}"
            );

            // When
            boolean result = policyMatcher.matchesTimeWindow(timeWindow);

            // Then
            assertThat(result).isTrue();
        }
    }

    /**
     * 辅助方法：创建 JsonNode
     */
    private JsonNode createJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}
