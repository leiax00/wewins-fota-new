package com.wewins.fota.application.upgrade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PolicyMatcher.matchesDevMode() 单元测试
 * <p>
 * 测试 dev 参数临时测试设备标注的匹配逻辑
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("dev 参数匹配测试")
class PolicyMatcherDevModeTest {

    @InjectMocks
    private PolicyMatcher policyMatcher;

    @Nested
    @DisplayName("策略没有环境限制时")
    class NoEnvironmentRestrictionTests {

        @Test
        @DisplayName("null 环境匹配 dev=1")
        void shouldMatchWhenEnvironmentIsNullWithDev1() {
            assertThat(policyMatcher.matchesDevMode(null, 1)).isTrue();
        }

        @Test
        @DisplayName("null 环境匹配 dev=0")
        void shouldMatchWhenEnvironmentIsNullWithDev0() {
            assertThat(policyMatcher.matchesDevMode(null, 0)).isTrue();
        }

        @Test
        @DisplayName("null 环境匹配 dev=null")
        void shouldMatchWhenEnvironmentIsNullWithDevNull() {
            assertThat(policyMatcher.matchesDevMode(null, null)).isTrue();
        }

        @Test
        @DisplayName("空字符串环境匹配 dev=1")
        void shouldMatchWhenEnvironmentIsEmptyWithDev1() {
            assertThat(policyMatcher.matchesDevMode("", 1)).isTrue();
        }

        @Test
        @DisplayName("空白字符串环境匹配 dev=1")
        void shouldMatchWhenEnvironmentIsBlankWithDev1() {
            assertThat(policyMatcher.matchesDevMode("  ", 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("测试环境策略 (test/dev)")
    class TestEnvironmentTests {

        @Test
        @DisplayName("test 环境匹配 dev=1")
        void shouldMatchWhenTestEnvWithDev1() {
            assertThat(policyMatcher.matchesDevMode("test", 1)).isTrue();
        }

        @Test
        @DisplayName("TEST 环境匹配 dev=1（大小写不敏感）")
        void shouldMatchWhenTestEnvUpperCaseWithDev1() {
            assertThat(policyMatcher.matchesDevMode("TEST", 1)).isTrue();
        }

        @Test
        @DisplayName("TeSt 环境匹配 dev=1（大小写不敏感）")
        void shouldMatchWhenTestEnvMixedCaseWithDev1() {
            assertThat(policyMatcher.matchesDevMode("TeSt", 1)).isTrue();
        }

        @Test
        @DisplayName("test 环境不匹配 dev=0")
        void shouldNotMatchWhenTestEnvWithDev0() {
            assertThat(policyMatcher.matchesDevMode("test", 0)).isFalse();
        }

        @Test
        @DisplayName("test 环境不匹配 dev=null")
        void shouldNotMatchWhenTestEnvWithDevNull() {
            assertThat(policyMatcher.matchesDevMode("test", null)).isFalse();
        }

        @Test
        @DisplayName("dev 环境匹配 dev=1")
        void shouldMatchWhenDevEnvWithDev1() {
            assertThat(policyMatcher.matchesDevMode("dev", 1)).isTrue();
        }

        @Test
        @DisplayName("DEV 环境匹配 dev=1（大小写不敏感）")
        void shouldMatchWhenDevEnvUpperCaseWithDev1() {
            assertThat(policyMatcher.matchesDevMode("DEV", 1)).isTrue();
        }

        @Test
        @DisplayName("dev 环境不匹配 dev=0")
        void shouldNotMatchWhenDevEnvWithDev0() {
            assertThat(policyMatcher.matchesDevMode("dev", 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("生产环境策略 (prod/production)")
    class ProductionEnvironmentTests {

        @Test
        @DisplayName("prod 环境匹配 dev=0")
        void shouldMatchWhenProdEnvWithDev0() {
            assertThat(policyMatcher.matchesDevMode("prod", 0)).isTrue();
        }

        @Test
        @DisplayName("PROD 环境匹配 dev=0（大小写不敏感）")
        void shouldMatchWhenProdEnvUpperCaseWithDev0() {
            assertThat(policyMatcher.matchesDevMode("PROD", 0)).isTrue();
        }

        @Test
        @DisplayName("prod 环境匹配 dev=null")
        void shouldMatchWhenProdEnvWithDevNull() {
            assertThat(policyMatcher.matchesDevMode("prod", null)).isTrue();
        }

        @Test
        @DisplayName("prod 环境不匹配 dev=1")
        void shouldNotMatchWhenProdEnvWithDev1() {
            assertThat(policyMatcher.matchesDevMode("prod", 1)).isFalse();
        }

        @Test
        @DisplayName("production 环境匹配 dev=0")
        void shouldMatchWhenProductionEnvWithDev0() {
            assertThat(policyMatcher.matchesDevMode("production", 0)).isTrue();
        }

        @Test
        @DisplayName("PRODUCTION 环境匹配 dev=0（大小写不敏感）")
        void shouldMatchWhenProductionEnvUpperCaseWithDev0() {
            assertThat(policyMatcher.matchesDevMode("PRODUCTION", 0)).isTrue();
        }

        @Test
        @DisplayName("production 环境匹配 dev=null")
        void shouldMatchWhenProductionEnvWithDevNull() {
            assertThat(policyMatcher.matchesDevMode("production", null)).isTrue();
        }

        @Test
        @DisplayName("production 环境不匹配 dev=1")
        void shouldNotMatchWhenProductionEnvWithDev1() {
            assertThat(policyMatcher.matchesDevMode("production", 1)).isFalse();
        }
    }

    @Nested
    @DisplayName("其他环境策略")
    class OtherEnvironmentTests {

        @Test
        @DisplayName("staging 环境匹配 dev=1")
        void shouldMatchWhenStagingEnvWithDev1() {
            assertThat(policyMatcher.matchesDevMode("staging", 1)).isTrue();
        }

        @Test
        @DisplayName("staging 环境匹配 dev=0")
        void shouldMatchWhenStagingEnvWithDev0() {
            assertThat(policyMatcher.matchesDevMode("staging", 0)).isTrue();
        }

        @Test
        @DisplayName("staging 环境匹配 dev=null")
        void shouldMatchWhenStagingEnvWithDevNull() {
            assertThat(policyMatcher.matchesDevMode("staging", null)).isTrue();
        }

        @Test
        @DisplayName("uat 环境匹配所有 dev 值")
        void shouldMatchWhenUatEnvWithAnyDev() {
            assertThat(policyMatcher.matchesDevMode("uat", 1)).isTrue();
            assertThat(policyMatcher.matchesDevMode("uat", 0)).isTrue();
            assertThat(policyMatcher.matchesDevMode("uat", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("带空格的环境名称应 trim 处理后匹配")
        void shouldTrimWhitespaceInEnvironment() {
            assertThat(policyMatcher.matchesDevMode(" test ", 1)).isTrue();
            assertThat(policyMatcher.matchesDevMode(" prod ", 0)).isTrue();
        }

        @Test
        @DisplayName("dev=2 视为非测试设备")
        void shouldTreatDev2AsNonTestDevice() {
            // dev=2 不是 1，所以不算测试设备
            assertThat(policyMatcher.matchesDevMode("test", 2)).isFalse();
            assertThat(policyMatcher.matchesDevMode("prod", 2)).isTrue();
        }

        @Test
        @DisplayName("dev=-1 视为非测试设备")
        void shouldTreatDevNegativeAsNonTestDevice() {
            assertThat(policyMatcher.matchesDevMode("test", -1)).isFalse();
            assertThat(policyMatcher.matchesDevMode("prod", -1)).isTrue();
        }

        @Test
        @DisplayName("环境字符串大小写混合带空格")
        void shouldHandleMixedCaseWithSpaces() {
            assertThat(policyMatcher.matchesDevMode("  TeSt  ", 1)).isTrue();
            assertThat(policyMatcher.matchesDevMode("  PrOd  ", 0)).isTrue();
        }
    }

    @Nested
    @DisplayName("实际使用场景")
    class RealWorldScenarios {

        @Test
        @DisplayName("场景：测试固件只给 dev=1 的设备")
        void scenarioTestFirmwareOnlyForDev1Devices() {
            String testPolicyEnv = "test";

            // 生产设备请求测试固件（dev=null）→ 不匹配
            assertThat(policyMatcher.matchesDevMode(testPolicyEnv, null)).isFalse();

            // 测试设备请求测试固件（dev=1）→ 匹配
            assertThat(policyMatcher.matchesDevMode(testPolicyEnv, 1)).isTrue();
        }

        @Test
        @DisplayName("场景：生产固件只给普通设备")
        void scenarioProductionFirmwareOnlyForNormalDevices() {
            String prodPolicyEnv = "prod";

            // 生产设备（dev=null）→ 匹配
            assertThat(policyMatcher.matchesDevMode(prodPolicyEnv, null)).isTrue();

            // 临时测试设备（dev=1）→ 不匹配
            assertThat(policyMatcher.matchesDevMode(prodPolicyEnv, 1)).isFalse();

            // 明确普通设备（dev=0）→ 匹配
            assertThat(policyMatcher.matchesDevMode(prodPolicyEnv, 0)).isTrue();
        }

        @Test
        @DisplayName("场景：无环境限制策略匹配所有设备")
        void scenarioNoRestrictionPolicyMatchesAll() {
            String nullPolicyEnv = null;

            // 所有设备都匹配
            assertThat(policyMatcher.matchesDevMode(nullPolicyEnv, null)).isTrue();
            assertThat(policyMatcher.matchesDevMode(nullPolicyEnv, 0)).isTrue();
            assertThat(policyMatcher.matchesDevMode(nullPolicyEnv, 1)).isTrue();
        }
    }
}
