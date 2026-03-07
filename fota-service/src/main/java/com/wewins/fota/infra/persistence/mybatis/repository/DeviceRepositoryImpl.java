package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.infra.persistence.converter.DeviceConverter;
import com.wewins.fota.infra.persistence.mybatis.dto.DeviceBatchUpdateDTO;
import com.wewins.fota.infra.persistence.mybatis.mapper.DeviceMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DevicePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceMapper deviceMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DeviceConverter deviceConverter;
    private final ObjectMapper objectMapper;

    @Override
    public List<Device> findByConditions(Long productId, String imei) {
        LambdaQueryWrapper<DevicePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(productId != null, DevicePO::getProductId, productId)
                .eq(imei != null && !imei.isBlank(), DevicePO::getImei, imei)
                .orderByDesc(DevicePO::getUpdatedAt);
        return deviceConverter.toDomainList(deviceMapper.selectList(wrapper));
    }

    @Override
    public Page<Device> pageDevices(Page<Device> page, Long productId, String imei, String status) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .isNull(DevicePO::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(DevicePO::getProductId, productId);
        }
        if (StringUtils.hasText(imei)) {
            queryWrapper.like(DevicePO::getImei, imei.trim());
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(DevicePO::getStatus, status.trim().toUpperCase());
        }

        queryWrapper.orderByDesc(DevicePO::getUpdatedAt);

        Page<DevicePO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<DevicePO> queried = deviceMapper.selectPage(poPage, queryWrapper);
        Page<Device> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(deviceConverter.toDomainList(queried.getRecords()));
        return result;
    }

    @Override
    public Optional<Device> findById(Long id) {
        return Optional.ofNullable(deviceConverter.toDomain(deviceMapper.selectById(id)));
    }

    @Override
    public Optional<Device> findByImei(String imei) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .isNull(DevicePO::getDeletedAt);
        queryWrapper.eq(DevicePO::getImei, imei);
        return Optional.ofNullable(deviceConverter.toDomain(deviceMapper.selectOne(queryWrapper)));
    }

    @Override
    public Device create(Device device) {
        DevicePO po = deviceConverter.toPo(device);
        deviceMapper.insert(po);
        return deviceConverter.toDomain(po);
    }

    @Override
    public Device updateById(Device device) {
        DevicePO po = deviceConverter.toPo(device);
        deviceMapper.updateById(po);
        return deviceConverter.toDomain(po);
    }

    @Override
    public long countByImeiExcludingId(String imei, Long excludeId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .eq(DevicePO::getImei, imei)
                .isNull(DevicePO::getDeletedAt);

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
        // 分批插入，避免单次插入过多数据
        int batchSize = 1000;
        for (int i = 0; i < devices.size(); i += batchSize) {
            int end = Math.min(i + batchSize, devices.size());
            List<Device> batch = devices.subList(i, end);
            for (Device device : batch) {
                deviceMapper.insert(deviceConverter.toPo(device));
            }
        }
    }

    @Override
    public Page<Device> pageByImportBatchId(Page<Device> page, Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .isNull(DevicePO::getDeletedAt);

        if (importBatchId != null) {
            queryWrapper.eq(DevicePO::getImportBatchId, importBatchId);
        }

        queryWrapper.orderByDesc(DevicePO::getCreatedAt);

        Page<DevicePO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<DevicePO> queried = deviceMapper.selectPage(poPage, queryWrapper);
        Page<Device> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(deviceConverter.toDomainList(queried.getRecords()));
        return result;
    }

    @Override
    public List<Device> findAllByImportBatchId(Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .isNull(DevicePO::getDeletedAt)
                .eq(DevicePO::getImportBatchId, importBatchId);
        return deviceConverter.toDomainList(deviceMapper.selectList(queryWrapper));
    }

    @Override
    public List<Device> findByImeis(List<String> imeis) {
        if (imeis == null || imeis.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .isNull(DevicePO::getDeletedAt)
                .in(DevicePO::getImei, imeis);
        return deviceConverter.toDomainList(deviceMapper.selectList(queryWrapper));
    }

    @Override
    public List<Device> findByConditions(Long productId, String imeiKeyword, String status, Long importBatchId) {
        LambdaQueryWrapper<DevicePO> queryWrapper = new LambdaQueryWrapper<DevicePO>()
                .isNull(DevicePO::getDeletedAt);

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

        return deviceConverter.toDomainList(deviceMapper.selectList(queryWrapper));
    }

    @Override
    public void batchUpdateTags(List<Long> deviceIds, String tagsJson) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }

        // 分批更新，避免SQL过长
        int batchSize = 1000;
        for (int i = 0; i < deviceIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, deviceIds.size());
            List<Long> batchIds = deviceIds.subList(i, end);

            // 使用JdbcTemplate直接执行SQL更新JSONB字段
            String sql = "UPDATE devices SET tags = ?::jsonb, updated_at = NOW() WHERE id = ANY(?) AND deleted_at IS NULL";
            jdbcTemplate.update(sql, tagsJson, batchIds.toArray(new Long[0]));
        }
    }

    @Override
    public void batchUpdateImportBatchId(List<Long> deviceIds, Long batchId) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }

        // 分批更新，避免 IN 子句过长
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
    public int batchSoftDelete(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }

        // 使用 JdbcTemplate 执行批量更新（更高效）
        String sql = "UPDATE devices SET deleted_at = NOW(), updated_at = NOW() " +
                     "WHERE id = ANY(?) AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, deviceIds.toArray(new Long[0]));
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
                    .versionPartsJson(toJsonString(device.getVersionParts()))
                    .initialVersionPartsJson(toJsonString(device.getInitialVersionParts()))
                    .build();

            batchList.add(dto);
        }

        if (batchList.isEmpty()) {
            return;
        }

        deviceMapper.batchUpdateDeviceInfo(batchList);
    }

    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", obj, e);
            return null;
        }
    }
}
