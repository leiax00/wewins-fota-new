package com.wewins.fota.application.upgrade;

import com.wewins.fota.domain.policy.model.enums.TimeWindowType;
import com.wewins.fota.domain.policy.model.vo.PolicyTimeWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyMatcher 单元测试")
class PolicyMatcherTest {

    private PolicyMatcher policyMatcher;

    @BeforeEach
    void setUp() {
        policyMatcher = new PolicyMatcher();
    }

    @Nested
    @DisplayName("matchesDeviceTags 方法测试")
    class MatchesDeviceTagsTests {

        @Test
        @DisplayName("策略没有标签要求 - 应该匹配")
        void shouldMatch_whenPolicyHasNoTags() {
            assertThat(policyMatcher.matchesDeviceTags(null, Map.of("env", "test"))).isTrue();
        }

        @Test
        @DisplayName("策略标签为空对象 - 应该匹配")
        void shouldMatch_whenPolicyTagsIsEmpty() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of(), Map.of("env", "test"))).isTrue();
        }

        @Test
        @DisplayName("设备没有标签且策略有要求 - 应该不匹配")
        void shouldNotMatch_whenDeviceHasNoTags() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("env", "test"), null)).isFalse();
        }

        @Test
        @DisplayName("设备标签为空对象且策略有要求 - 应该不匹配")
        void shouldNotMatch_whenDeviceTagsIsEmpty() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("env", "test"), Map.of())).isFalse();
        }

        @Test
        @DisplayName("设备标签包含策略要求且有额外标签 - 应该匹配")
        void shouldMatch_whenDeviceTagsContainsRequiredTags() {
            Map<String, Object> policyTags = Map.of("env", "test", "region", "CN");
            Map<String, String> deviceTags = Map.of("env", "test", "region", "CN", "network", "5G");
            assertThat(policyMatcher.matchesDeviceTags(policyTags, deviceTags)).isTrue();
        }

        @Test
        @DisplayName("设备标签缺少必需的键 - 应该不匹配")
        void shouldNotMatch_whenDeviceTagsMissingRequiredKey() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("env", "test", "region", "CN"),
                    Map.of("env", "test"))).isFalse();
        }

        @Test
        @DisplayName("设备标签值不匹配 - 应该不匹配")
        void shouldNotMatch_whenDeviceTagValueMismatch() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("env", "test", "region", "CN"),
                    Map.of("env", "test", "region", "US"))).isFalse();
        }
    }

    @Nested
    @DisplayName("值类型匹配测试")
    class ValueTypeTests {

        @Test
        @DisplayName("数值匹配")
        void shouldMatch_numericValues() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("level", 1), Map.of("level", "1"))).isTrue();
        }

        @Test
        @DisplayName("布尔值匹配")
        void shouldMatch_booleanValues() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("enabled", true), Map.of("enabled", "true"))).isTrue();
        }

        @Test
        @DisplayName("null 值不匹配非 null 设备值")
        void shouldNotMatch_nullRequiredValueWithActualValue() {
            Map<String, Object> policyTags = new HashMap<>();
            policyTags.put("disabled", null);
            assertThat(policyMatcher.matchesDeviceTags(policyTags, Map.of("disabled", "false"))).isFalse();
        }

        @Test
        @DisplayName("数值类型不匹配 - 整数 vs 浮点数字符串相等值")
        void shouldMatch_numericEquivalentValues() {
            assertThat(policyMatcher.matchesDeviceTags(Map.of("level", 1), Map.of("level", "1.0"))).isTrue();
        }
    }

    @Nested
    @DisplayName("实际场景测试")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("场景：多条件组合匹配")
        void testMultipleConditionsScenario() {
            Map<String, Object> policyTags = Map.of("env", "test", "user_level", "VIP", "region", "CN");
            Map<String, String> deviceTags = Map.of(
                    "env", "test",
                    "user_level", "VIP",
                    "region", "CN",
                    "network", "5G"
            );
            assertThat(policyMatcher.matchesDeviceTags(policyTags, deviceTags)).isTrue();
        }

        @Test
        @DisplayName("场景：部分条件满足不应匹配")
        void testPartialConditionsShouldNotMatch() {
            Map<String, Object> policyTags = Map.of("env", "test", "user_level", "VIP", "region", "CN");
            Map<String, String> deviceTags = Map.of("env", "test", "user_level", "VIP", "region", "US");
            assertThat(policyMatcher.matchesDeviceTags(policyTags, deviceTags)).isFalse();
        }
    }

    @Nested
    @DisplayName("matchesTimeWindow 方法测试")
    class MatchesTimeWindowTests {

        @Test
        @DisplayName("无时间窗口限制 - 应该匹配")
        void shouldMatch_whenNoTimeWindow() {
            assertThat(policyMatcher.matchesTimeWindow(null)).isTrue();
        }

        @Test
        @DisplayName("空类型时间窗口 - 应该匹配")
        void shouldMatch_whenTypeIsNull() {
            assertThat(policyMatcher.matchesTimeWindow(PolicyTimeWindow.builder().build())).isTrue();
        }

        @Test
        @DisplayName("RANGE 类型 - 当前时间在窗口内 - 应该匹配")
        void shouldMatch_whenInRangeWindow() {
            PolicyTimeWindow timeWindow = PolicyTimeWindow.builder()
                    .type(TimeWindowType.RANGE)
                    .startAt(LocalDateTime.ofInstant(Instant.now().minusSeconds(3600), ZoneOffset.UTC))
                    .endAt(LocalDateTime.ofInstant(Instant.now().plusSeconds(3600), ZoneOffset.UTC))
                    .build();
            assertThat(policyMatcher.matchesTimeWindow(timeWindow)).isTrue();
        }

        @Test
        @DisplayName("RANGE 类型 - 当前时间在窗口外 - 应该不匹配")
        void shouldNotMatch_whenOutsideRangeWindow() {
            PolicyTimeWindow timeWindow = PolicyTimeWindow.builder()
                    .type(TimeWindowType.RANGE)
                    .startAt(LocalDateTime.ofInstant(Instant.now().plusSeconds(7200), ZoneOffset.UTC))
                    .endAt(LocalDateTime.ofInstant(Instant.now().plusSeconds(10800), ZoneOffset.UTC))
                    .build();
            assertThat(policyMatcher.matchesTimeWindow(timeWindow)).isFalse();
        }

        @Test
        @DisplayName("DAILY 类型 - 方法可正常执行")
        void shouldExecute_whenDailyWindowType() {
            PolicyTimeWindow timeWindow = PolicyTimeWindow.builder()
                    .type(TimeWindowType.DAILY)
                    .startAt(LocalDateTime.of(2026, 2, 1, 8, 0))
                    .endAt(LocalDateTime.of(2026, 2, 1, 12, 0))
                    .build();
            assertThat(policyMatcher.matchesTimeWindow(timeWindow)).isNotNull();
        }

        @Test
        @DisplayName("缺少时间字段 - 应该匹配")
        void shouldMatch_whenTimeWindowMissingTimes() {
            PolicyTimeWindow timeWindow = PolicyTimeWindow.builder()
                    .type(TimeWindowType.RANGE)
                    .build();
            assertThat(policyMatcher.matchesTimeWindow(timeWindow)).isTrue();
        }
    }
}
