package com.wewins.fota.application.device;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.model.vo.DeviceCache;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceInfoUpdateAppServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceCacheRepository deviceCacheRepository;

    @InjectMocks
    private DeviceInfoUpdateAppService deviceInfoUpdateAppService;

    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        testTime = LocalDateTime.now();
    }

    @Test
    void processBatch_WhenMessagesNull_ShouldReturnEarly() {
        deviceInfoUpdateAppService.processBatch(null);

        verify(deviceRepository, never()).applyCheckUpdates(anyList());
        verify(deviceCacheRepository, never()).put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processBatch_WhenMessagesEmpty_ShouldReturnEarly() {
        deviceInfoUpdateAppService.processBatch(List.of());

        verify(deviceRepository, never()).applyCheckUpdates(anyList());
        verify(deviceCacheRepository, never()).put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processBatch_ShouldApplyUpdatesAndRefreshCache() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .imei("123456789012345")
                .deviceId(1L)
                .productId(10L)
                .accessTime(testTime)
                .currentVersionParts(Map.of(
                        "main",
                        DeviceVersionPart.builder()
                                .versionId(102L)
                                .version("2.0.0")
                                .internalVersion("build-2")
                                .updatedAt(testTime)
                                .build()
                ))
                .build();

        deviceInfoUpdateAppService.processBatch(List.of(message));

        verify(deviceRepository, times(1)).applyCheckUpdates(List.of(message));

        ArgumentCaptor<DeviceCache> cacheCaptor = ArgumentCaptor.forClass(DeviceCache.class);
        verify(deviceCacheRepository, times(1)).put(org.mockito.ArgumentMatchers.eq("123456789012345"), cacheCaptor.capture());
        DeviceCache cache = cacheCaptor.getValue();
        assertEquals(1L, cache.getDeviceId());
        assertEquals(10L, cache.getProductId());
        assertNotNull(cache.getVersionParts());
        assertEquals(102L, cache.getVersionParts().getParts().get("main").getVersionId());
        assertEquals("2.0.0", cache.getVersionParts().getParts().get("main").getVersion());
        assertEquals("build-2", cache.getVersionParts().getParts().get("main").getInternalVersion());
    }

    @Test
    void processBatch_WhenDuplicateImeis_ShouldKeepLatestMessage() {
        DeviceInfoUpdateMessage first = DeviceInfoUpdateMessage.builder()
                .imei("123456789012345")
                .deviceId(1L)
                .productId(10L)
                .accessTime(testTime.minusSeconds(10))
                .build();
        DeviceInfoUpdateMessage latest = DeviceInfoUpdateMessage.builder()
                .imei("123456789012345")
                .deviceId(1L)
                .productId(10L)
                .accessTime(testTime)
                .currentVersionParts(Map.of(
                        "main",
                        DeviceVersionPart.builder()
                                .versionId(102L)
                                .version("2.0.0")
                                .updatedAt(testTime)
                                .build()
                ))
                .build();

        deviceInfoUpdateAppService.processBatch(List.of(first, latest));

        verify(deviceRepository, times(1)).applyCheckUpdates(List.of(latest));
    }

    @Test
    void processBatch_WhenCacheWriteFails_ShouldNotBlockDatabaseApply() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .imei("123456789012345")
                .deviceId(1L)
                .productId(10L)
                .accessTime(testTime)
                .build();
        doThrow(new RuntimeException("cache error"))
                .when(deviceCacheRepository)
                .put(org.mockito.ArgumentMatchers.eq("123456789012345"), org.mockito.ArgumentMatchers.any(DeviceCache.class));

        assertDoesNotThrow(() -> deviceInfoUpdateAppService.processBatch(List.of(message)));

        verify(deviceRepository, times(1)).applyCheckUpdates(List.of(message));
    }

    @Test
    void processBatch_WhenCacheVersionPartsMissing_ShouldWriteEmptyVersionParts() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .imei("123456789012345")
                .deviceId(1L)
                .productId(10L)
                .accessTime(testTime)
                .build();

        deviceInfoUpdateAppService.processBatch(List.of(message));

        ArgumentCaptor<DeviceCache> cacheCaptor = ArgumentCaptor.forClass(DeviceCache.class);
        verify(deviceCacheRepository, times(1)).put(org.mockito.ArgumentMatchers.eq("123456789012345"), cacheCaptor.capture());
        DeviceCache cache = cacheCaptor.getValue();
        assertNotNull(cache.getVersionParts());
        assertEquals(0, cache.getVersionParts().getParts().size());
        assertNull(cache.getVersionParts().getParts().get("main"));
    }
}
