package com.wewins.fota.application.device.support;

import com.wewins.fota.application.device.query.DeviceImportBatchNameQueryService;
import com.wewins.fota.application.firmware.query.FirmwareVersionNameQueryService;
import com.wewins.fota.application.product.query.ProductNameQueryService;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeviceDisplayNameService {

    private final ProductNameQueryService productNameQueryService;
    private final FirmwareVersionNameQueryService firmwareVersionNameQueryService;
    private final DeviceImportBatchNameQueryService deviceImportBatchNameQueryService;

    public DeviceDisplayNameService(
            ProductNameQueryService productNameQueryService,
            FirmwareVersionNameQueryService firmwareVersionNameQueryService,
            DeviceImportBatchNameQueryService deviceImportBatchNameQueryService
    ) {
        this.productNameQueryService = productNameQueryService;
        this.firmwareVersionNameQueryService = firmwareVersionNameQueryService;
        this.deviceImportBatchNameQueryService = deviceImportBatchNameQueryService;
    }

    public DeviceNameContext resolveForDevices(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return new DeviceNameContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        Set<Long> productIds = devices.stream()
                .map(Device::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> versionIds = collectFirmwareVersionIds(devices);
        Set<Long> batchIds = devices.stream()
                .map(Device::getImportBatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new DeviceNameContext(
                productNameQueryService.resolveProductNames(productIds),
                firmwareVersionNameQueryService.resolveFirmwareVersionNames(versionIds),
                deviceImportBatchNameQueryService.resolveImportBatchNames(batchIds)
        );
    }

    public DeviceNameContext resolveForDevice(Device device) {
        if (device == null) {
            return new DeviceNameContext(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
        return resolveForDevices(List.of(device));
    }

    private Set<Long> collectFirmwareVersionIds(List<Device> devices) {
        Set<Long> ids = new HashSet<>();
        for (Device device : devices) {
            ids.addAll(collectFirmwareVersionIds(device));
        }
        return ids;
    }

    private Set<Long> collectFirmwareVersionIds(Device device) {
        Set<Long> ids = new HashSet<>();
        if (device == null) {
            return ids;
        }
        DeviceVersionParts versionParts = device.getVersionParts();
        DeviceVersionParts initialVersionParts = device.getInitialVersionParts();
        if (versionParts != null && versionParts.hasVersion()) {
            ids.addAll(versionParts.getVersionIds());
        }
        if (initialVersionParts != null && initialVersionParts.hasVersion()) {
            ids.addAll(initialVersionParts.getVersionIds());
        }
        return ids;
    }
}
