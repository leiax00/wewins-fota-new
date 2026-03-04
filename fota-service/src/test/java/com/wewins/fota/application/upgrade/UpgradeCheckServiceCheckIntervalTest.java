package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.upgrade.dto.CheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UpgradeCheckService 检查间隔调整功能测试
 * <p>
 * 测试 adjustCheckInterval 方法的各种场景
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("检查间隔调整测试")
class UpgradeCheckServiceCheckIntervalTest {

    private Method adjustCheckIntervalMethod;

    @BeforeEach
    void setUp() throws Exception {
        // 通过反射获取 private 方法用于测试
        adjustCheckIntervalMethod = UpgradeCheckService.class
                .getDeclaredMethod("adjustCheckInterval", CheckResult.class, Integer.class);
        adjustCheckIntervalMethod.setAccessible(true);
    }

    /**
     * 调用 private 方法进行测试
     */
    private void invokeAdjustCheckInterval(CheckResult result, Integer auto) throws Exception {
        adjustCheckIntervalMethod.invoke(new UpgradeCheckService(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null), result, auto);
    }

    @Nested
    @DisplayName("adjustCheckInterval 方法测试")
    class AdjustCheckIntervalTests {

        @Test
        @DisplayName("auto=0 (手动检查) - 应设置 3600 秒")
        void whenAutoIsZero_shouldSet3600Seconds() throws Exception {
            // Given
            CheckResult result = CheckResult.builder().build();
            Integer auto = 0;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(3600);
        }

        @Test
        @DisplayName("auto=1 (自动检查) - 应设置 86400 秒")
        void whenAutoIsOne_shouldSet86400Seconds() throws Exception {
            // Given
            CheckResult result = CheckResult.builder().build();
            Integer auto = 1;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(86400);
        }

        @Test
        @DisplayName("auto=null (默认) - 应设置 3600 秒")
        void whenAutoIsNull_shouldSet3600Seconds() throws Exception {
            // Given
            CheckResult result = CheckResult.builder().build();
            Integer auto = null;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(3600);
        }

        @Test
        @DisplayName("已有间隔值 - 不应覆盖")
        void whenIntervalAlreadySet_shouldNotOverride() throws Exception {
            // Given
            CheckResult result = CheckResult.builder()
                    .responseCheckInterval(7200)
                    .build();
            Integer auto = 1;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(7200);
        }

        @Test
        @DisplayName("auto=2 (非标准值) - 应视为手动检查")
        void whenAutoIsTwo_shouldTreatAsManualCheck() throws Exception {
            // Given
            CheckResult result = CheckResult.builder().build();
            Integer auto = 2;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(3600);
        }

        @Test
        @DisplayName("auto=-1 (负数) - 应视为手动检查")
        void whenAutoIsNegative_shouldTreatAsManualCheck() throws Exception {
            // Given
            CheckResult result = CheckResult.builder().build();
            Integer auto = -1;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(3600);
        }

        @Test
        @DisplayName("限流结果保留原间隔值")
        void whenRateLimited_shouldKeepOriginalInterval() throws Exception {
            // Given
            CheckResult result = CheckResult.rateLimited("请求过于频繁", 300);
            Integer auto = 0;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(300);
        }

        @Test
        @DisplayName("noUpdate 结果有默认间隔 86400")
        void whenNoUpdate_shouldHaveDefaultInterval() {
            // Given & When
            CheckResult result = CheckResult.noUpdate();

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(86400);
        }

        @Test
        @DisplayName("多次调用 auto=0 - 间隔保持 3600")
        void whenCalledMultipleTimesWithAutoZero_shouldStay3600() throws Exception {
            // Given
            CheckResult result = CheckResult.builder().build();

            // When - 调用多次
            invokeAdjustCheckInterval(result, 0);
            invokeAdjustCheckInterval(result, 0);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(3600);
        }

        @Test
        @DisplayName("间隔为 0 的特殊值 - 不应覆盖")
        void whenIntervalIsZero_shouldNotOverride() throws Exception {
            // Given
            CheckResult result = CheckResult.builder()
                    .responseCheckInterval(0)
                    .build();
            Integer auto = 1;

            // When
            invokeAdjustCheckInterval(result, auto);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("checkUpgrade 方法签名测试")
    class CheckUpgradeSignatureTests {

        @Test
        @DisplayName("存在单参数方法 checkUpgrade(String)")
        void shouldHaveSingleParameterMethod() throws Exception {
            // Given & When
            Method method = UpgradeCheckService.class
                    .getMethod("checkUpgrade", String.class);

            // Then
            assertThat(method).isNotNull();
            assertThat(method.getParameterCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("存在双参数方法 checkUpgrade(String, Integer)")
        void shouldHaveTwoParameterMethod() throws Exception {
            // Given & When
            Method method = UpgradeCheckService.class
                    .getMethod("checkUpgrade", String.class, Integer.class);

            // Then
            assertThat(method).isNotNull();
            assertThat(method.getParameterCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("CheckResult 静态工厂方法测试")
    class CheckResultFactoryMethodsTests {

        @Test
        @DisplayName("noUpdate() 默认间隔 86400")
        void noUpdate_defaultInterval86400() {
            // When
            CheckResult result = CheckResult.noUpdate();

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(86400);
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("NO_UPDATE");
        }

        @Test
        @DisplayName("notFound() 间隔为 null")
        void notFound_intervalIsNull() {
            // When
            CheckResult result = CheckResult.notFound("设备不存在");

            // Then
            assertThat(result.getResponseCheckInterval()).isNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
        }

        @Test
        @DisplayName("error() 间隔为 null")
        void error_intervalIsNull() {
            // When
            CheckResult result = CheckResult.error("配置无效");

            // Then
            assertThat(result.getResponseCheckInterval()).isNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("rateLimited() 设置指定间隔")
        void rateLimited_setsSpecifiedInterval() {
            // When
            CheckResult result = CheckResult.rateLimited("限流", 120);

            // Then
            assertThat(result.getResponseCheckInterval()).isEqualTo(120);
            assertThat(result.getDownloadDelay()).isEqualTo(120);
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("RATE_LIMITED");
        }
    }
}
