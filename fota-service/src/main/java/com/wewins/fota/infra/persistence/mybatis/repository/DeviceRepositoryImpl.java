package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.infra.persistence.converter.DeviceConverter;
import com.wewins.fota.infra.persistence.mybatis.dto.DeviceBatchUpdateDTO;
import com.wewins.fota.infra.persistence.mybatis.mapper.DeviceInitialVersionPartMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.DeviceMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.DeviceTagMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.DeviceVersionPartMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.FirmwareVersionMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceInitialVersionPartPO;
import com.wewins.fota.infra.persistence.mybatis.po.DevicePO;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceTagPO;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceVersionPartPO;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceMapper deviceMapper;
    private final DeviceConverter deviceConverter;
    private final ObjectMapper objectMapper;
    private final DeviceTagMapper deviceTagMapper;
    private final DeviceVersionPartMapper deviceVersionPartMapper;
    private final DeviceInitialVersionPartMapper deviceInitialVersionPartMapper;
    private final FirmwareVersionMapper firmwareVersionMapper;

    @Override
    public List<Device> findByConditions(Long productId, String imei) {
        LambdaQueryWrapper<DevicePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(productId != null, DevicePO::getProductId, productId)
                .eq(imei != null && !imei.isBlank(), DevicePO::getImei, imei)
                .orderByDesc(DevicePO::getUpdatedAt);
        return enrichDevices(deviceConverter.toDomainList(deviceMapper.selectList(wrapper)));
    }

    @Override
    public Page<Device> pageDevices(Page<Device> page, Long productId, String imei, String status, Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<>();

        if (productId != null) {
            queryWrapper.eq(DevicePO::getProductId, productId);
        }
        if (StringUtils.hasText(imei)) {
            queryWrapper.like(DevicePO::getImei, imei.trim());
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(DevicePO::getStatus, status.trim().toUpperCase());
        }
        if (importBatchId != null) {
            queryWrapper.eq(DevicePO::getImportBatchId, importBatchId);
        }

        queryWrapper.orderByDesc(DevicePO::getUpdatedAt);

        Page<DevicePO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<DevicePO> queried = deviceMapper.selectPage(poPage, queryWrapper);
        Page<Device> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(enrichDevices(deviceConverter.toDomainList(queried.getRecords())));
        return result;
    }

    @Override
    public Optional<Device> findById(Long id) {
        return Optional.ofNullable(enrichDevice(deviceConverter.toDomain(deviceMapper.selectById(id))));
    }

    @Override
    public Optional<Device> findByImei(String imei) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DevicePO::getImei, imei);
        return Optional.ofNullable(enrichDevice(deviceConverter.toDomain(deviceMapper.selectOne(queryWrapper))));
    }

    @Override
    public Device create(Device device) {
        DevicePO po = deviceConverter.toPo(device);
        deviceMapper.insert(po);
        device.setId(po.getId());
        device.setCreatedAt(po.getCreatedAt());
        device.setCreatedBy(po.getCreatedBy());
        device.setUpdatedAt(po.getUpdatedAt());
        device.setUpdatedBy(po.getUpdatedBy());
        syncRelations(device);
        return enrichDevice(device);
    }

    @Override
    public Device updateById(Device device) {
        DevicePO po = deviceConverter.toPo(device);
        deviceMapper.updateById(po);
        device.setUpdatedAt(po.getUpdatedAt());
        device.setUpdatedBy(po.getUpdatedBy());
        syncRelations(device);
        return enrichDevice(device);
    }

    @Override
    public long countByImeiExcludingId(String imei, Long excludeId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .eq(DevicePO::getImei, imei);

        if (excludeId != null) {
            queryWrapper.ne(DevicePO::getId, excludeId);
        }

        return deviceMapper.selectCount(queryWrapper);
    }

    @Override
    public boolean softDeleteById(Long id) {
        return deviceMapper.deleteById(id) > 0;
    }

    @Override
    public void batchCreate(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        int batchSize = 1000;
        for (int i = 0; i < devices.size(); i += batchSize) {
            int end = Math.min(i + batchSize, devices.size());
            List<Device> batch = devices.subList(i, end);
            for (Device device : batch) {
                DevicePO po = deviceConverter.toPo(device);
                deviceMapper.insert(po);
                device.setId(po.getId());
                device.setCreatedAt(po.getCreatedAt());
                device.setCreatedBy(po.getCreatedBy());
                device.setUpdatedAt(po.getUpdatedAt());
                device.setUpdatedBy(po.getUpdatedBy());
                syncRelations(device);
            }
        }
    }

    @Override
    public Page<Device> pageByImportBatchId(Page<Device> page, Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<>();

        if (importBatchId != null) {
            queryWrapper.eq(DevicePO::getImportBatchId, importBatchId);
        }

        queryWrapper.orderByDesc(DevicePO::getCreatedAt);

        Page<DevicePO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<DevicePO> queried = deviceMapper.selectPage(poPage, queryWrapper);
        Page<Device> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(enrichDevices(deviceConverter.toDomainList(queried.getRecords())));
        return result;
    }

    @Override
    public List<Device> findAllByImportBatchId(Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .eq(DevicePO::getImportBatchId, importBatchId);
        return enrichDevices(deviceConverter.toDomainList(deviceMapper.selectList(queryWrapper)));
    }

    @Override
    public List<Device> findByImeis(List<String> imeis) {
        if (imeis == null || imeis.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .in(DevicePO::getImei, imeis);
        return enrichDevices(deviceConverter.toDomainList(deviceMapper.selectList(queryWrapper)));
    }

    @Override
    public List<Device> findByConditions(Long productId, String imeiKeyword, String status, Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<>();

        if (productId != null) {
            queryWrapper.eq(DevicePO::getProductId, productId);
        }
        if (StringUtils.hasText(imeiKeyword)) {
            queryWrapper.like(DevicePO::getImei, imeiKeyword.trim());
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(DevicePO::getStatus, status.trim().toUpperCase());
        }
        if (importBatchId != null) {
            queryWrapper.eq(DevicePO::getImportBatchId, importBatchId);
        }

        queryWrapper.orderByDesc(DevicePO::getUpdatedAt);

        return enrichDevices(deviceConverter.toDomainList(deviceMapper.selectList(queryWrapper)));
    }

    @Override
    public void batchUpdateTags(List<Long> deviceIds, String tagsJson) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }

        int batchSize = 1000;
        Map<String, String> tags = parseStringMap(tagsJson);
        for (int i = 0; i < deviceIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, deviceIds.size());
            List<Long> batchIds = deviceIds.subList(i, end);

            LambdaUpdateWrapper<DevicePO> updateWrapper = new LambdaUpdateWrapper<DevicePO>()
                    .set(DevicePO::getUpdatedAt, java.time.LocalDateTime.now())
                    .in(DevicePO::getId, batchIds);
            deviceMapper.update(null, updateWrapper);
            replaceDeviceTags(batchIds, tags);
        }
    }

    @Override
    public void batchUpdateImportBatchId(List<Long> deviceIds, Long batchId) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }

        int batchSize = 1000;
        for (int i = 0; i < deviceIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, deviceIds.size());
            List<Long> batchIds = deviceIds.subList(i, end);

            LambdaUpdateWrapper<DevicePO> updateWrapper = new LambdaUpdateWrapper<DevicePO>()
                    .set(DevicePO::getImportBatchId, batchId)
                    .in(DevicePO::getId, batchIds);

            deviceMapper.update(null, updateWrapper);
        }
    }

    @Override
    public int batchDelete(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }

        int batchSize = 1000;
        int totalDeleted = 0;

        for (int i = 0; i < deviceIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, deviceIds.size());
            List<Long> batchIds = deviceIds.subList(i, end);

            LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                    .in(DevicePO::getId, batchIds);

            totalDeleted += deviceMapper.delete(queryWrapper);
        }

        return totalDeleted;
    }

    @Override
    public void updateBatch(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }

        List<DeviceBatchUpdateDTO> batchList = new ArrayList<>(devices.size());
        for (Device device : devices) {
            if (device.getId() == null) {
                continue;
            }

            DeviceBatchUpdateDTO dto = DeviceBatchUpdateDTO.builder()
                    .id(device.getId())
                    .firstSeenAt(device.getFirstSeenAt())
                    .lastSeenAt(device.getLastSeenAt())
                    .build();

            batchList.add(dto);
        }

        if (batchList.isEmpty()) {
            return;
        }

        deviceMapper.batchUpdateDeviceInfo(batchList);
        replaceDeviceVersionParts(devices);
        replaceDeviceInitialVersionParts(devices);
    }

    private List<Device> enrichDevices(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return devices;
        }

        List<Long> deviceIds = devices.stream().map(Device::getId).filter(Objects::nonNull).toList();
        if (deviceIds.isEmpty()) {
            return devices;
        }

        Map<Long, Map<String, String>> tagsByDeviceId = deviceTagMapper.selectByDeviceIds(deviceIds).stream()
                .collect(Collectors.groupingBy(
                        DeviceTagPO::getDeviceId,
                        LinkedHashMap::new,
                        Collectors.toMap(DeviceTagPO::getTagKey, DeviceTagPO::getTagValue, (a, b) -> b, LinkedHashMap::new)
                ));
        Map<Long, DeviceVersionParts> versionPartsByDeviceId = buildDeviceVersionParts(deviceVersionPartMapper.selectByDeviceIds(deviceIds));
        Map<Long, DeviceVersionParts> initialVersionPartsByDeviceId = buildInitialVersionParts(deviceInitialVersionPartMapper.selectByDeviceIds(deviceIds));

        devices.forEach(device -> {
            Map<String, String> tags = tagsByDeviceId.get(device.getId());
            if (tags != null && !tags.isEmpty()) {
                device.setTags(tags);
            }
            DeviceVersionParts versionParts = versionPartsByDeviceId.get(device.getId());
            if (versionParts != null) {
                device.setVersionParts(versionParts);
            }
            DeviceVersionParts initialVersionParts = initialVersionPartsByDeviceId.get(device.getId());
            if (initialVersionParts != null) {
                device.setInitialVersionParts(initialVersionParts);
            }
        });

        return devices;
    }

    private Device enrichDevice(Device device) {
        if (device == null || device.getId() == null) {
            return device;
        }
        return enrichDevices(new ArrayList<>(List.of(device))).getFirst();
    }

    private Map<Long, DeviceVersionParts> buildDeviceVersionParts(List<DeviceVersionPartPO> rows) {
        Map<Long, DeviceVersionParts> result = new LinkedHashMap<>();
        for (DeviceVersionPartPO row : rows) {
            DeviceVersionParts parts = result.computeIfAbsent(row.getDeviceId(), id -> DeviceVersionParts.builder().build());
            parts.getParts().put(row.getPartName(), DeviceVersionPart.builder()
                    .versionId(row.getVersionId())
                    .version(row.getVersion())
                    .internalVersion(row.getInternalVersion())
                    .updatedAt(row.getUpdatedAt())
                    .build());
            if (row.getIsPrimary() != null && row.getIsPrimary() == 1) {
                parts.setPrimaryPart(row.getPartName());
            }
        }
        return result;
    }

    private Map<Long, DeviceVersionParts> buildInitialVersionParts(List<DeviceInitialVersionPartPO> rows) {
        Map<Long, DeviceVersionParts> result = new LinkedHashMap<>();
        for (DeviceInitialVersionPartPO row : rows) {
            DeviceVersionParts parts = result.computeIfAbsent(row.getDeviceId(), id -> DeviceVersionParts.builder().build());
            parts.getParts().put(row.getPartName(), DeviceVersionPart.builder()
                    .versionId(row.getVersionId())
                    .version(row.getVersion())
                    .internalVersion(row.getInternalVersion())
                    .updatedAt(row.getRecordedAt())
                    .build());
            if (row.getIsPrimary() != null && row.getIsPrimary() == 1) {
                parts.setPrimaryPart(row.getPartName());
            }
        }
        return result;
    }

    private void syncRelations(Device device) {
        if (device == null || device.getId() == null) {
            return;
        }
        replaceDeviceTags(List.of(device.getId()), device.getTags());
        replaceDeviceVersionParts(List.of(device));
        replaceDeviceInitialVersionParts(List.of(device));
    }

    private void replaceDeviceTags(List<Long> deviceIds, Map<String, String> tags) {
        deviceTagMapper.deleteByDeviceIds(deviceIds);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<DeviceTagPO> rows = new ArrayList<>();
        for (Long deviceId : deviceIds) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                DeviceTagPO row = new DeviceTagPO();
                row.setDeviceId(deviceId);
                row.setTagKey(entry.getKey());
                row.setTagValue(entry.getValue());
                rows.add(row);
            }
        }
        deviceTagMapper.batchInsert(rows);
    }

    private void replaceDeviceVersionParts(List<Device> devices) {
        List<Long> deviceIds = devices.stream().map(Device::getId).filter(id -> id != null).toList();
        if (deviceIds.isEmpty()) {
            return;
        }
        deviceVersionPartMapper.deleteByDeviceIds(deviceIds);
        List<DeviceVersionPartPO> rows = buildDeviceVersionPartRows(devices);
        if (!rows.isEmpty()) {
            deviceVersionPartMapper.batchInsert(rows);
        }
    }

    private void replaceDeviceInitialVersionParts(List<Device> devices) {
        List<Long> deviceIds = devices.stream().map(Device::getId).filter(id -> id != null).toList();
        if (deviceIds.isEmpty()) {
            return;
        }
        deviceInitialVersionPartMapper.deleteByDeviceIds(deviceIds);
        List<DeviceInitialVersionPartPO> rows = buildDeviceInitialVersionPartRows(devices);
        if (!rows.isEmpty()) {
            deviceInitialVersionPartMapper.batchInsert(rows);
        }
    }

    private List<DeviceVersionPartPO> buildDeviceVersionPartRows(List<Device> devices) {
        Map<Long, FirmwareVersionPO> firmwareById = loadFirmwareVersions(devices, false);
        List<DeviceVersionPartPO> rows = new ArrayList<>();
        for (Device device : devices) {
            DeviceVersionParts parts = device.getVersionParts();
            if (device.getId() == null || parts == null || parts.getParts() == null || parts.getParts().isEmpty()) {
                continue;
            }
            String primaryPart = StringUtils.hasText(parts.getPrimaryPart()) ? parts.getPrimaryPart() : "main";
            for (Map.Entry<String, DeviceVersionPart> entry : parts.getParts().entrySet()) {
                DeviceVersionPart part = entry.getValue();
                if (part == null) {
                    continue;
                }
                FirmwareVersionPO firmware = part.getVersionId() == null ? null : firmwareById.get(part.getVersionId());
                DeviceVersionPartPO row = new DeviceVersionPartPO();
                row.setDeviceId(device.getId());
                row.setPartName(entry.getKey());
                row.setVersionId(part.getVersionId());
                row.setVersion(firstNonBlank(part.getVersion(), firmware == null ? null : firmware.getVersion(), "UNKNOWN"));
                row.setInternalVersion(firstNonBlank(part.getInternalVersion(), firmware == null ? null : firmware.getInternalVersion(), null));
                row.setIsPrimary(primaryPart.equals(entry.getKey()) ? 1 : 0);
                row.setUpdatedAt(part.getUpdatedAt() != null ? part.getUpdatedAt() : device.getLastSeenAt());
                rows.add(row);
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(DeviceVersionPartPO::getDeviceId).thenComparing(DeviceVersionPartPO::getPartName))
                .toList();
    }

    private List<DeviceInitialVersionPartPO> buildDeviceInitialVersionPartRows(List<Device> devices) {
        Map<Long, FirmwareVersionPO> firmwareById = loadFirmwareVersions(devices, true);
        List<DeviceInitialVersionPartPO> rows = new ArrayList<>();
        for (Device device : devices) {
            DeviceVersionParts parts = device.getInitialVersionParts();
            if (device.getId() == null || parts == null || parts.getParts() == null || parts.getParts().isEmpty()) {
                continue;
            }
            String primaryPart = StringUtils.hasText(parts.getPrimaryPart()) ? parts.getPrimaryPart() : "main";
            for (Map.Entry<String, DeviceVersionPart> entry : parts.getParts().entrySet()) {
                DeviceVersionPart part = entry.getValue();
                if (part == null) {
                    continue;
                }
                FirmwareVersionPO firmware = part.getVersionId() == null ? null : firmwareById.get(part.getVersionId());
                DeviceInitialVersionPartPO row = new DeviceInitialVersionPartPO();
                row.setDeviceId(device.getId());
                row.setPartName(entry.getKey());
                row.setVersionId(part.getVersionId());
                row.setVersion(firstNonBlank(part.getVersion(), firmware == null ? null : firmware.getVersion(), "UNKNOWN"));
                row.setInternalVersion(firstNonBlank(part.getInternalVersion(), firmware == null ? null : firmware.getInternalVersion(), null));
                row.setIsPrimary(primaryPart.equals(entry.getKey()) ? 1 : 0);
                row.setRecordedAt(part.getUpdatedAt() != null ? part.getUpdatedAt() : device.getFirstSeenAt());
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<Long, FirmwareVersionPO> loadFirmwareVersions(List<Device> devices, boolean initial) {
        List<Long> versionIds = devices.stream()
                .map(device -> initial ? device.getInitialVersionParts() : device.getVersionParts())
                .filter(parts -> parts != null && parts.getParts() != null)
                .flatMap(parts -> parts.getParts().values().stream())
                .filter(part -> part != null && part.getVersionId() != null)
                .map(DeviceVersionPart::getVersionId)
                .distinct()
                .toList();
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return firmwareVersionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(FirmwareVersionPO::getId, Function.identity(), (a, b) -> b));
    }

    private Map<String, String> parseStringMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (Exception e) {
            log.warn("解析设备标签 JSON 失败: {}", json, e);
            return null;
        }
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }
}
