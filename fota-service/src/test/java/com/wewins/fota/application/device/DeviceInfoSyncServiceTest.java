package com.wewins.fota.application.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.value.DeviceVersionParts;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceInfoSyncServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceCacheRepository deviceCacheRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;

    @InjectMocks
    private DeviceInfoSyncService deviceInfoSyncService;

    private Device device;
    private Product product;
    private FirmwareVersion firmwareVersion;
    private UpgradeCheckReqDTO request;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setModel("test-product");

        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("part", "main");
        meta.put("i18n", objectMapper.createObjectNode()
                .put("description", "Main firmware"));

        firmwareVersion = new FirmwareVersion();
        firmwareVersion.setId(101L);
        firmwareVersion.setProductId(1L);
        firmwareVersion.setVersion("1.0.0");
        firmwareVersion.setMeta(meta);

        device = new Device();
        device.setId(1L);
        device.setImei("123456789012345");
        device.setProductId(1L);
        device.setStatus("ACTIVE");

        request = new UpgradeCheckReqDTO();
        request.setProduct("test-product");
        request.setImei("123456789012345");
    }

    @Test
    void testSyncDeviceInfo_WhenDeviceIsNull_ShouldReturnEarly() {
        deviceInfoSyncService.syncDeviceInfo(null, request);
        
        verify(deviceRepository, never()).updateById(any());
        verify(deviceCacheRepository, never()).evict(any());
    }

    @Test
    void testSyncDeviceInfo_WhenRequestIsNull_ShouldReturnEarly() {
        deviceInfoSyncService.syncDeviceInfo(device, null);
        
        verify(deviceRepository, never()).updateById(any());
        verify(deviceCacheRepository, never()).evict(any());
    }

    @Test
    void testSyncDeviceInfo_WhenProductMismatch_ShouldIgnoreUpdate() {
        request.setProduct("different-product");
        
        Product differentProduct = new Product();
        differentProduct.setId(2L);
        
        when(productRepository.findByModel("different-product"))
                .thenReturn(Optional.of(differentProduct));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        verify(deviceRepository, never()).updateById(any());
        verify(deviceCacheRepository, never()).evict(any());
    }

    @Test
    void testSyncDeviceInfo_WhenProductNotFound_ShouldIgnoreUpdate() {
        request.setProduct("non-existent-product");
        
        when(productRepository.findByModel("non-existent-product"))
                .thenReturn(Optional.empty());

        deviceInfoSyncService.syncDeviceInfo(device, request);

        verify(deviceRepository, never()).updateById(any());
        verify(deviceCacheRepository, never()).evict(any());
    }

    @Test
    void testSyncVersion_WhenVersionChanged_ShouldUpdate() {
        request.setVersion("1.0.0");

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        verify(deviceRepository, times(1)).updateById(device);
        verify(deviceCacheRepository, times(1)).evict("123456789012345");
        
        assertNotNull(device.getVersionParts());
        assertEquals(101L, device.getVersionParts().getPrimaryVersionId());
        assertNotNull(device.getLastSeenAt());
        assertNotNull(device.getFirstSeenAt());
        assertNotNull(device.getInitialVersionParts());
    }

    @Test
    void testSyncVersion_WhenVersionNotChanged_ShouldStillUpdateLastSeenAt() {
        request.setVersion("1.0.0");

        DeviceVersionParts existingParts = DeviceVersionParts.builder().build();
        existingParts.updatePart("main", 101L, "1.0.0", LocalDateTime.now().minusDays(1));
        device.setVersionParts(existingParts);
        device.setFirstSeenAt(LocalDateTime.now().minusDays(1));

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        verify(deviceRepository, times(1)).updateById(device);
        verify(deviceCacheRepository, times(1)).evict("123456789012345");
        assertNotNull(device.getLastSeenAt());
    }

    @Test
    void testFirstSeenAt_WhenFirstCheck_ShouldBeInitialized() {
        request.setVersion("1.0.0");

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        assertNull(device.getFirstSeenAt());

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertNotNull(device.getFirstSeenAt());
        assertNotNull(device.getLastSeenAt());
    }

    @Test
    void testFirstSeenAt_WhenSubsequentCheck_ShouldNotChange() {
        request.setVersion("1.0.0");

        LocalDateTime originalFirstSeen = LocalDateTime.now().minusDays(10);
        device.setFirstSeenAt(originalFirstSeen);

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertEquals(originalFirstSeen, device.getFirstSeenAt());
        assertNotNull(device.getLastSeenAt());
    }

    @Test
    void testInitialVersionParts_WhenFirstCheck_ShouldBeInitialized() {
        request.setVersion("1.0.0");

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        assertNull(device.getInitialVersionParts());

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertNotNull(device.getInitialVersionParts());
        assertEquals(101L, device.getInitialVersionParts().getPrimaryVersionId());
    }

    @Test
    void testInitialVersionParts_WhenSubsequentCheck_ShouldNotChange() {
        request.setVersion("2.0.0");

        DeviceVersionParts originalInitial = DeviceVersionParts.builder().build();
        originalInitial.updatePart("main", 100L, "0.9.0", LocalDateTime.now().minusDays(10));
        device.setInitialVersionParts(originalInitial);

        FirmwareVersion newVersion = new FirmwareVersion();
        newVersion.setId(102L);
        newVersion.setVersion("2.0.0");
        ObjectNode newMeta = objectMapper.createObjectNode().put("part", "main");
        newVersion.setMeta(newMeta);

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "2.0.0"))
                .thenReturn(Collections.singletonList(newVersion));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertEquals(originalInitial, device.getInitialVersionParts());
        assertEquals(100L, device.getInitialVersionParts().getPrimaryVersionId());
        assertEquals(102L, device.getVersionParts().getPrimaryVersionId());
    }

    @Test
    void testPartExtraction_FromMeta_ShouldExtractPartName() {
        request.setVersion("1.0.0");

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertNotNull(device.getVersionParts());
        assertTrue(device.getVersionParts().getParts().containsKey("main"));
    }

    @Test
    void testPartExtraction_WhenNoMeta_ShouldDefaultToMain() {
        request.setVersion("1.0.0");

        FirmwareVersion versionWithoutMeta = new FirmwareVersion();
        versionWithoutMeta.setId(101L);
        versionWithoutMeta.setVersion("1.0.0");
        versionWithoutMeta.setMeta(null);

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(versionWithoutMeta));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertNotNull(device.getVersionParts());
        assertTrue(device.getVersionParts().getParts().containsKey("main"));
    }

    @Test
    void testPartExtraction_WhenDifferentPart_ShouldUpdateCorrectPart() {
        request.setVersion("2.1.0");

        FirmwareVersion bootloaderVersion = new FirmwareVersion();
        bootloaderVersion.setId(201L);
        bootloaderVersion.setVersion("2.1.0");
        ObjectNode bootloaderMeta = objectMapper.createObjectNode().put("part", "bootloader");
        bootloaderVersion.setMeta(bootloaderMeta);

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "2.1.0"))
                .thenReturn(Collections.singletonList(bootloaderVersion));

        deviceInfoSyncService.syncDeviceInfo(device, request);

        assertNotNull(device.getVersionParts());
        assertTrue(device.getVersionParts().getParts().containsKey("bootloader"));
        assertEquals(201L, device.getVersionParts().getParts().get("bootloader").getVersionId());
    }

    @Test
    void testLastSeenAt_AlwaysUpdated() {
        request.setVersion("1.0.0");

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(firmwareVersion));

        LocalDateTime before = LocalDateTime.now();
        deviceInfoSyncService.syncDeviceInfo(device, request);
        LocalDateTime after = LocalDateTime.now();

        assertNotNull(device.getLastSeenAt());
        assertTrue(device.getLastSeenAt().isAfter(before.minusSeconds(1)));
        assertTrue(device.getLastSeenAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void multiplePartsCheck_ShouldUpdateDifferentParts() {
        device.setVersionParts(null);
        device.setFirstSeenAt(null);

        FirmwareVersion mainVersion = new FirmwareVersion();
        mainVersion.setId(101L);
        mainVersion.setVersion("1.0.0");
        ObjectNode mainMeta = objectMapper.createObjectNode().put("part", "main");
        mainVersion.setMeta(mainMeta);

        FirmwareVersion bootloaderVersion = new FirmwareVersion();
        bootloaderVersion.setId(201L);
        bootloaderVersion.setVersion("2.1.0");
        ObjectNode bootloaderMeta = objectMapper.createObjectNode().put("part", "bootloader");
        bootloaderVersion.setMeta(bootloaderMeta);

        when(productRepository.findByModel("test-product"))
                .thenReturn(Optional.of(product));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "1.0.0"))
                .thenReturn(Collections.singletonList(mainVersion));
        when(firmwareVersionRepository.findByProductIdAndVersion(1L, "2.1.0"))
                .thenReturn(Collections.singletonList(bootloaderVersion));

        request.setVersion("1.0.0");
        deviceInfoSyncService.syncDeviceInfo(device, request);

        request.setVersion("2.1.0");
        deviceInfoSyncService.syncDeviceInfo(device, request);

        verify(deviceRepository, times(2)).updateById(device);
        verify(deviceCacheRepository, times(2)).evict("123456789012345");

        assertNotNull(device.getVersionParts());
        assertEquals(101L, device.getVersionParts().getParts().get("main").getVersionId());
        assertEquals(201L, device.getVersionParts().getParts().get("bootloader").getVersionId());
    }
}
