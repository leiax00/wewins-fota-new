package com.wewins.fota.infra.cache;

import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.firmware.cache.FirmwareCacheRepository;
import com.wewins.fota.domain.policy.cache.PolicyCacheRepository;
import com.wewins.fota.domain.product.cache.ProductCacheRepository;
import com.wewins.fota.infra.cache.event.ChangeType;
import com.wewins.fota.infra.cache.event.DeviceChangedEvent;
import com.wewins.fota.infra.cache.event.FirmwareChangedEvent;
import com.wewins.fota.infra.cache.event.PolicyChangedEvent;
import com.wewins.fota.infra.cache.event.ProductChangedEvent;
import com.wewins.fota.infra.cache.metrics.CacheMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("缓存失效集成测试")
class CacheInvalidationIntegrationTest {

    @Mock
    private ProductCacheInvalidator productCacheInvalidator;
    @Mock
    private PolicyCacheInvalidator policyCacheInvalidator;
    @Mock
    private FirmwareCacheInvalidator firmwareCacheInvalidator;
    @Mock
    private DeviceCacheRepository deviceCacheRepository;
    @Mock
    private ProductCacheRepository productCacheRepository;
    @Mock
    private PolicyCacheRepository policyCacheRepository;
    @Mock
    private FirmwareCacheRepository firmwareCacheRepository;
    @Mock
    private CacheMetricsService cacheMetricsService;

    @InjectMocks
    private CacheInvalidationListener listener;

    @Nested
    @DisplayName("产品缓存失效测试")
    class ProductCacheInvalidationTests {

        @Test
        @DisplayName("产品 CREATED 事件应触发缓存失效")
        void onProductCreated_shouldInvalidateCache() {
            ProductChangedEvent event = new ProductChangedEvent(this, 1L, ChangeType.CREATED);

            listener.onProductChanged(event);

            verify(productCacheInvalidator).invalidateOnProductChange(1L);
        }

        @Test
        @DisplayName("产品 UPDATED 事件应触发缓存失效")
        void onProductUpdated_shouldInvalidateCache() {
            ProductChangedEvent event = new ProductChangedEvent(this, 1L, ChangeType.UPDATED);

            listener.onProductChanged(event);

            verify(productCacheInvalidator).invalidateOnProductChange(1L);
        }

        @Test
        @DisplayName("产品 DELETED 事件应触发缓存失效")
        void onProductDeleted_shouldInvalidateCache() {
            ProductChangedEvent event = new ProductChangedEvent(this, 1L, ChangeType.DELETED);

            listener.onProductChanged(event);

            verify(productCacheInvalidator).invalidateOnProductChange(1L);
        }

        @Test
        @DisplayName("产品 STATUS_CHANGED 事件应触发状态相关缓存失效")
        void onProductStatusChanged_shouldInvalidateStatusCache() {
            ProductChangedEvent event = new ProductChangedEvent(this, 1L, ChangeType.STATUS_CHANGED);

            listener.onProductChanged(event);

            verify(productCacheInvalidator).invalidateOnProductStatusChange(1L);
        }
    }

    @Nested
    @DisplayName("策略缓存失效测试")
    class PolicyCacheInvalidationTests {

        @Test
        @DisplayName("策略 CREATED 事件应触发缓存失效")
        void onPolicyCreated_shouldInvalidateCache() {
            PolicyChangedEvent event = new PolicyChangedEvent(this, 1L, 100L, ChangeType.CREATED);

            listener.onPolicyChanged(event);

            verify(policyCacheInvalidator).invalidateOnPolicyChange(1L, 100L);
        }

        @Test
        @DisplayName("策略 UPDATED 事件应触发缓存失效")
        void onPolicyUpdated_shouldInvalidateCache() {
            PolicyChangedEvent event = new PolicyChangedEvent(this, 1L, 100L, ChangeType.UPDATED);

            listener.onPolicyChanged(event);

            verify(policyCacheInvalidator).invalidateOnPolicyChange(1L, 100L);
        }

        @Test
        @DisplayName("策略 DELETED 事件应触发缓存失效")
        void onPolicyDeleted_shouldInvalidateCache() {
            PolicyChangedEvent event = new PolicyChangedEvent(this, 1L, 100L, ChangeType.DELETED);

            listener.onPolicyChanged(event);

            verify(policyCacheInvalidator).invalidateOnPolicyChange(1L, 100L);
        }

        @Test
        @DisplayName("策略 STATUS_CHANGED 事件应触发缓存失效")
        void onPolicyStatusChanged_shouldInvalidateCache() {
            PolicyChangedEvent event = new PolicyChangedEvent(this, 1L, 100L, ChangeType.STATUS_CHANGED);

            listener.onPolicyChanged(event);

            verify(policyCacheInvalidator).invalidateOnPolicyChange(1L, 100L);
        }
    }

    @Nested
    @DisplayName("固件缓存失效测试")
    class FirmwareCacheInvalidationTests {

        @Test
        @DisplayName("固件 CREATED 事件应触发缓存失效")
        void onFirmwareCreated_shouldInvalidateCache() {
            FirmwareChangedEvent event = new FirmwareChangedEvent(this, 1L, 10L, "v2.0.0", "build-1", ChangeType.CREATED);

            listener.onFirmwareChanged(event);

            verify(firmwareCacheInvalidator).invalidateOnFirmwarePublish(1L, 10L, "v2.0.0", "build-1");
        }

        @Test
        @DisplayName("固件 UPDATED 事件应触发缓存失效")
        void onFirmwareUpdated_shouldInvalidateCache() {
            FirmwareChangedEvent event = new FirmwareChangedEvent(this, 1L, 10L, "v2.0.0", "build-1", ChangeType.UPDATED);

            listener.onFirmwareChanged(event);

            verify(firmwareCacheInvalidator).invalidateOnFirmwarePublish(1L, 10L, "v2.0.0", "build-1");
        }

        @Test
        @DisplayName("固件 DELETED 事件应触发缓存失效")
        void onFirmwareDeleted_shouldInvalidateCache() {
            FirmwareChangedEvent event = new FirmwareChangedEvent(this, 1L, 10L, "v2.0.0", "build-1", ChangeType.DELETED);

            listener.onFirmwareChanged(event);

            verify(firmwareCacheInvalidator).invalidateOnFirmwarePublish(1L, 10L, "v2.0.0", "build-1");
        }
    }

    @Nested
    @DisplayName("设备缓存失效测试")
    class DeviceCacheInvalidationTests {

        @Test
        @DisplayName("设备 CREATED 事件应触发缓存驱逐")
        void onDeviceCreated_shouldEvictCache() {
            DeviceChangedEvent event = new DeviceChangedEvent(this, "354972069009027", ChangeType.CREATED);

            listener.onDeviceChanged(event);

            verify(deviceCacheRepository).evict("354972069009027");
        }

        @Test
        @DisplayName("设备 UPDATED 事件应触发缓存驱逐")
        void onDeviceUpdated_shouldEvictCache() {
            DeviceChangedEvent event = new DeviceChangedEvent(this, "354972069009027", ChangeType.UPDATED);

            listener.onDeviceChanged(event);

            verify(deviceCacheRepository).evict("354972069009027");
        }

        @Test
        @DisplayName("设备 DELETED 事件应触发缓存驱逐")
        void onDeviceDeleted_shouldEvictCache() {
            DeviceChangedEvent event = new DeviceChangedEvent(this, "354972069009027", ChangeType.DELETED);

            listener.onDeviceChanged(event);

            verify(deviceCacheRepository).evict("354972069009027");
        }
    }

    @Nested
    @DisplayName("ChangeType 枚举测试")
    class ChangeTypeEnumTests {

        @Test
        @DisplayName("ChangeType 包含所有变更类型")
        void changeType_shouldContainAllTypes() {
            assertThat(ChangeType.values()).containsExactlyInAnyOrder(
                    ChangeType.CREATED,
                    ChangeType.UPDATED,
                    ChangeType.DELETED,
                    ChangeType.STATUS_CHANGED,
                    ChangeType.BATCH_IMPORTED
            );
        }
    }
}
