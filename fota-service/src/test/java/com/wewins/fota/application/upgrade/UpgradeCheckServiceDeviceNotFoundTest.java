package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CheckResult 静态工厂方法测试
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("CheckResult 静态工厂方法测试")
class UpgradeCheckServiceDeviceNotFoundTest {

    @Nested
    @DisplayName("CheckResult.notFound() 测试")
    class CheckResultNotFoundTests {

        @Test
        @DisplayName("notFound() 方法应创建正确的 NOT_FOUND 结果")
        void testNotFoundStaticFactory() {
            String errorMessage = "设备未注册，请联系管理员";

            CheckResult result = CheckResult.notFound("req-123", errorMessage);

            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.DEVICE_NOT_FOUND);
            assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("notFound(null) 应能正常工作")
        void testNotFoundWithNullMessage() {
            CheckResult result = CheckResult.notFound("req-123", null);

            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.DEVICE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("CheckResult.noUpdate() 测试")
    class CheckResultNoUpdateTests {

        @Test
        @DisplayName("noUpdate() 方法应创建正确的 NO_UPDATE 结果")
        void testNoUpdateStaticFactory() {
            CheckResult result = CheckResult.noUpdate("req-456");

            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.NO_UPDATE);
            assertThat(result.getCheckInterval()).isEqualTo(86400);
        }
    }

    @Nested
    @DisplayName("CheckResult.rateLimited() 测试")
    class CheckResultRateLimitedTests {

        @Test
        @DisplayName("rateLimited() 方法应创建正确的 RATE_LIMITED 结果")
        void testRateLimitedStaticFactory() {
            CheckResult result = CheckResult.rateLimited("req-789", "请求过于频繁", 300);

            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.RATE_LIMITED);
            assertThat(result.getErrorMessage()).isEqualTo("请求过于频繁");
            assertThat(result.getCheckInterval()).isEqualTo(300);
            assertThat(result.getDownloadDelay()).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("CheckResult.error() 测试")
    class CheckResultErrorTests {

        @Test
        @DisplayName("error() 方法应创建正确的 ERROR 结果")
        void testErrorStaticFactory() {
            CheckResult result = CheckResult.error("req-abc", "系统错误");

            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.ERROR);
            assertThat(result.getErrorMessage()).isEqualTo("系统错误");
            assertThat(result.getCheckInterval()).isEqualTo(3600);
        }

        @Test
        @DisplayName("error() 方法带错误码应正常工作")
        void testErrorWithErrorCode() {
            CheckResult result = CheckResult.error("req-def", "ERR_001", "配置无效");

            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo(UpgradeDecision.ERROR);
            assertThat(result.getErrorCode()).isEqualTo("ERR_001");
            assertThat(result.getErrorMessage()).isEqualTo("配置无效");
        }
    }

    @Nested
    @DisplayName("结果类型区分测试")
    class CheckResultTypeTests {

        @Test
        @DisplayName("不同结果类型应能正确区分")
        void testDifferentResultTypes() {
            CheckResult notFoundResult = CheckResult.notFound("req-1", "设备未注册，请联系管理员");
            CheckResult noUpdateResult = CheckResult.noUpdate("req-2");
            CheckResult errorResult = CheckResult.error("req-3", "系统错误");
            CheckResult rateLimitedResult = CheckResult.rateLimited("req-4", "限流", 60);

            assertThat(notFoundResult.getDecision()).isEqualTo(UpgradeDecision.DEVICE_NOT_FOUND);
            assertThat(noUpdateResult.getDecision()).isEqualTo(UpgradeDecision.NO_UPDATE);
            assertThat(errorResult.getDecision()).isEqualTo(UpgradeDecision.ERROR);
            assertThat(rateLimitedResult.getDecision()).isEqualTo(UpgradeDecision.RATE_LIMITED);

            assertThat(notFoundResult.getHasUpdate()).isFalse();
            assertThat(noUpdateResult.getHasUpdate()).isFalse();
            assertThat(errorResult.getHasUpdate()).isFalse();
            assertThat(rateLimitedResult.getHasUpdate()).isFalse();
        }
    }
}
