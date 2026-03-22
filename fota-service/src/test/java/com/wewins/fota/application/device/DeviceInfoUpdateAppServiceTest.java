package com.wewins.fota.application.device;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceInfoUpdateAppServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceCacheRepository deviceCacheRepository;

    @InjectMocks
    private DeviceInfoUpdateAppService deviceInfoUpdateAppService;

    private Device device1;
    private Device device2;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        testTime = LocalDateTime.now();

        device1 = new Device();
        device1.setId(1L);
        device1.setImei("123456789012345");
        device1.setProductId(1L);
        device1.setStatus("ACTIVE");

        device2 = new Device();
        device2.setId(2L);
        device2.setImei("987654321098765");
        device2.setProductId(1L);
        device2.setStatus("ACTIVE");
    }

    @Test
    void processBatch_WhenMessagesNull_ShouldReturnEarly() {
        deviceInfoUpdateAppService.processBatch(null);

        verify(deviceRepository, never()).findByImeis(any());
        verify(deviceRepository, never()).updateBatch(any());
    }

    @Test
    void processBatch_WhenMessagesEmpty_ShouldReturnEarly() {
        deviceInfoUpdateAppService.processBatch(new ArrayList<>());

        verify(deviceRepository, never()).findByImeis(any());
        verify(deviceRepository, never()).updateBatch(any());
    }

    @Test
    void processBatch_WhenFirstOnline_ShouldInitializeFirstSeenAt() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        assertNotNull(device1.getFirstSeenAt());
        assertEquals(testTime, device1.getFirstSeenAt());
        assertNotNull(device1.getLastSeenAt());
        verify(deviceRepository, times(1)).updateBatch(anyList());
        verify(deviceCacheRepository, times(1)).put(eq("123456789012345"), any());
    }

    @Test
    void processBatch_WhenFirstOnline_ShouldInitializeInitialVersionParts() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .newVersion("1.0.0")
                .newVersionId(101L)
                .partName("main")
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        assertNotNull(device1.getInitialVersionParts());
        assertTrue(device1.getInitialVersionParts().getParts().containsKey("main"));
        assertEquals(101L, device1.getInitialVersionParts().getParts().get("main").getVersionId());
    }

    @Test
    void processBatch_WhenVersionChanged_ShouldUpdateVersionParts() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(false)
                .newVersion("2.0.0")
                .newVersionId(102L)
                .partName("main")
                .oldVersion("1.0.0")
                .oldVersionId(101L)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.VERSION_CHANGED)
                .build();

        DeviceVersionParts existingParts = DeviceVersionParts.builder().build();
        existingParts.updatePart("main", 101L, testTime.minusDays(1));
        device1.setVersionParts(existingParts);
        device1.setFirstSeenAt(testTime.minusDays(10));

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        assertEquals(102L, device1.getVersionParts().getParts().get("main").getVersionId());
        assertNull(device1.getVersionParts().getParts().get("main").getVersion());
    }

    @Test
    void processBatch_WhenAccessTimeUpdate_ShouldUpdateLastSeenAt() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(false)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.ACCESS_TIME_UPDATE)
                .build();

        DeviceVersionParts existingParts = DeviceVersionParts.builder().build();
        existingParts.updatePart("main", 101L, testTime.minusDays(1));
        device1.setVersionParts(existingParts);
        device1.setFirstSeenAt(testTime.minusDays(10));

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        assertEquals(testTime, device1.getLastSeenAt());
        verify(deviceRepository, times(1)).updateBatch(anyList());
    }

    @Test
    void processBatch_WhenDeviceNotFound_ShouldSkip() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .accessTime(testTime)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(Collections.emptyList());

        deviceInfoUpdateAppService.processBatch(List.of(message));

        verify(deviceRepository, never()).updateBatch(any());
        verify(deviceCacheRepository, never()).evict(any());
    }

    @Test
    void processBatch_WhenMultipleDevices_ShouldProcessAll() {
        DeviceInfoUpdateMessage message1 = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        DeviceInfoUpdateMessage message2 = DeviceInfoUpdateMessage.builder()
                .messageId("msg-2")
                .imei("987654321098765")
                .deviceId(2L)
                .productId(1L)
                .isFirstOnline(true)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1, device2));

        deviceInfoUpdateAppService.processBatch(List.of(message1, message2));

        assertNotNull(device1.getFirstSeenAt());
        assertNotNull(device2.getFirstSeenAt());
        verify(deviceRepository, times(1)).updateBatch(anyList());
        verify(deviceCacheRepository, times(1)).put(eq("123456789012345"), any());
        verify(deviceCacheRepository, times(1)).put(eq("987654321098765"), any());
    }

    @Test
    void processBatch_WhenDuplicateImeis_ShouldDeduplicate() {
        DeviceInfoUpdateMessage message1 = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        DeviceInfoUpdateMessage message2 = DeviceInfoUpdateMessage.builder()
                .messageId("msg-2")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .newVersion("2.0.0")
                .newVersionId(102L)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.VERSION_CHANGED)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message1, message2));

        verify(deviceRepository, times(1)).findByImeis(List.of("123456789012345"));
    }

    @Test
    void processBatch_WhenCacheEvictFails_ShouldContinue() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));
        lenient().doThrow(new RuntimeException("Cache error")).when(deviceCacheRepository).evict("123456789012345");

        assertDoesNotThrow(() -> deviceInfoUpdateAppService.processBatch(List.of(message)));

        verify(deviceRepository, times(1)).updateBatch(anyList());
    }

    @Test
    void processBatch_WhenVersionNotChanged_ShouldNotUpdateVersionParts() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(false)
                .newVersion("1.0.0")
                .newVersionId(101L)
                .partName("main")
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.ACCESS_TIME_UPDATE)
                .build();

        DeviceVersionParts existingParts = DeviceVersionParts.builder().build();
        existingParts.updatePart("main", 101L, testTime.minusDays(1));
        device1.setVersionParts(existingParts);
        device1.setFirstSeenAt(testTime.minusDays(10));

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        verify(deviceRepository, times(1)).updateBatch(anyList());
        assertNotNull(device1.getLastSeenAt());
    }

    @Test
    void processBatch_WhenDifferentParts_ShouldUpdateCorrectPart() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(false)
                .newVersion("2.1.0")
                .newVersionId(201L)
                .partName("bootloader")
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.VERSION_CHANGED)
                .build();

        DeviceVersionParts existingParts = DeviceVersionParts.builder().build();
        existingParts.updatePart("main", 101L, testTime.minusDays(1));
        device1.setVersionParts(existingParts);
        device1.setFirstSeenAt(testTime.minusDays(10));

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        assertEquals(101L, device1.getVersionParts().getParts().get("main").getVersionId());
        assertEquals(201L, device1.getVersionParts().getParts().get("bootloader").getVersionId());
        assertNull(device1.getVersionParts().getParts().get("bootloader").getVersion());
    }

    @Test
    void processBatch_WhenNoPartName_ShouldDefaultToMain() {
        DeviceInfoUpdateMessage message = DeviceInfoUpdateMessage.builder()
                .messageId("msg-1")
                .imei("123456789012345")
                .deviceId(1L)
                .productId(1L)
                .isFirstOnline(true)
                .newVersion("1.0.0")
                .newVersionId(101L)
                .accessTime(testTime)
                .updateReason(DeviceInfoUpdateMessage.UpdateReason.FIRST_ONLINE)
                .build();

        when(deviceRepository.findByImeis(anyList())).thenReturn(List.of(device1));

        deviceInfoUpdateAppService.processBatch(List.of(message));

        assertTrue(device1.getVersionParts().getParts().containsKey("main"));
        assertEquals(101L, device1.getVersionParts().getParts().get("main").getVersionId());
    }
}
