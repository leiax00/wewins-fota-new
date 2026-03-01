package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.upgrade.UpgradeCheckService.CheckResult;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UpgradeCheckService 设备不存在拒绝逻辑测试
 * <p>
 * 测试设备不存在时返回 NOT_FOUND 的逻辑
 * </p>
 *
 * <p>业务规则：</p>
 * <ul>
 *   <li>新系统要求设备必须预先导入</li>
 *   <li>设备不存在时拒绝升级，不创建设备</li>
 *   <li>返回明确的错误消息："设备未注册，请联系管理员"</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("设备不存在拒绝逻辑测试")
@ExtendWith(MockitoExtension.class)
class UpgradeCheckServiceDeviceNotFoundTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCacheRepository deviceCacheService;
    @Mock
    private DataIntegrityService dataIntegrityService;
    @Mock
    private UpgradePolicyRepository upgradePolicyRepository;
    @Mock
    private DeviceRateLimiter deviceRateLimiter;
    @Mock
    private DeviceActivityBitmapRepository bitmapRepository;
    @Mock
    private PolicyMatcher policyMatcher;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;
    @Mock
    private FirmwareVersionLookupService firmwareVersionLookupService;
    @Mock
    private GrayReleaseService grayReleaseService;
    @Mock
    private UpgradeResponseBuilder upgradeResponseBuilder;

    private UpgradeCheckService upgradeCheckService;

    @BeforeEach
    void setUp() {
        upgradeCheckService = new UpgradeCheckService(
                deviceRepository,
                deviceCacheService,
                dataIntegrityService,
                upgradePolicyRepository,
                deviceRateLimiter,
                bitmapRepository,
                policyMatcher,
                productRepository,
                firmwareVersionRepository,
                firmwareVersionLookupService,
                grayReleaseService,
                upgradeResponseBuilder
        );
    }

    @Nested
    @DisplayName("设备不存在拒绝逻辑测试")
    class DeviceNotFoundTests {

        @Test
        @DisplayName("设备不存在 - 应返回 NOT_FOUND")
        void whenDeviceNotFound_shouldReturnNotFound() {
            // Given
            String imei = "123456789012345";
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
            assertThat(result.getErrorMessage()).isEqualTo("设备未注册，请联系管理员");
        }

        @Test
        @DisplayName("设备不存在 - auto=1 时也应返回 NOT_FOUND")
        void whenDeviceNotFoundWithAuto_shouldReturnNotFound() {
            // Given
            String imei = "123456789012345";
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(imei, 1);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
            assertThat(result.getErrorMessage()).isEqualTo("设备未注册，请联系管理员");
        }

        @Test
        @DisplayName("设备不存在 - auto=0 时也应返回 NOT_FOUND")
        void whenDeviceNotFoundWithManualAuto_shouldReturnNotFound() {
            // Given
            String imei = "123456789012345";
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(imei, 0);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
            assertThat(result.getErrorMessage()).isEqualTo("设备未注册，请联系管理员");
        }

        @Test
        @DisplayName("设备不存在 - 不应设置检查间隔")
        void whenDeviceNotFound_checkIntervalShouldBeNull() {
            // Given
            String imei = "123456789012345";
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Then
            assertThat(result.getResponseCheckInterval()).isNull();
        }
    }

    @Nested
    @DisplayName("CheckResult.notFound() 静态工厂方法测试")
    class CheckResultNotFoundTests {

        @Test
        @DisplayName("notFound() 方法应创建正确的 NOT_FOUND 结果")
        void testNotFoundStaticFactory() {
            // Given
            String errorMessage = "设备未注册，请联系管理员";

            // When
            CheckResult result = CheckResult.notFound(errorMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
            assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("notFound(null) 应使用默认消息")
        void testNotFoundWithNullMessage() {
            // When
            CheckResult result = CheckResult.notFound(null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHasUpdate()).isFalse();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
        }

        @Test
        @DisplayName("notFound() 与其他结果类型应能区分")
        void testNotFoundVsOtherResults() {
            // Given
            CheckResult notFoundResult = CheckResult.notFound("设备未注册，请联系管理员");
            CheckResult noUpdateResult = CheckResult.noUpdate();
            CheckResult errorResult = CheckResult.error("系统错误");

            // Then & When
            assertThat(notFoundResult.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
            assertThat(noUpdateResult.getDecision()).isEqualTo("NO_UPDATE");
            assertThat(errorResult.getDecision()).isEqualTo("ERROR");

            assertThat(notFoundResult.getHasUpdate()).isFalse();
            assertThat(noUpdateResult.getHasUpdate()).isFalse();
            assertThat(errorResult.getHasUpdate()).isFalse();
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("空 IMEI 字符串应被正常处理")
        void whenEmptyImei_shouldHandleGracefully() {
            // Given
            String imei = "";
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
        }

        @Test
        @DisplayName("特殊字符 IMEI 应被正常处理")
        void whenSpecialCharImei_shouldHandleGracefully() {
            // Given
            String imei = "'; DROP TABLE devices; --";
            when(deviceRateLimiter.allow(anyString(), any(), any()))
                    .thenReturn(RateLimitDecision.allowed());
            when(deviceRepository.findByImei(imei)).thenReturn(Optional.empty());

            // When
            CheckResult result = upgradeCheckService.checkUpgrade(imei);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDecision()).isEqualTo("DEVICE_NOT_FOUND");
        }
    }
}
