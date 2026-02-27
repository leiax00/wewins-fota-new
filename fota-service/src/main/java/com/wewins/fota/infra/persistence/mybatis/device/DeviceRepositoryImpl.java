package com.wewins.fota.infra.persistence.mybatis.device;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.infra.persistence.mybatis.device.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * DeviceRepository 的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceMapper deviceMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Device> findByConditions(Long productId, String imei) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(productId != null, Device::getProductId, productId)
                .eq(imei != null && !imei.isBlank(), Device::getImei, imei)
                .orderByDesc(Device::getUpdatedAt);
        return deviceMapper.selectList(wrapper);
    }

    @Override
    public Page<Device> pageDevices(Page<Device> page, Long productId, String imei, String status) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<Device>()
                .isNull(Device::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(Device::getProductId, productId);
        }
        if (StringUtils.hasText(imei)) {
            queryWrapper.like(Device::getImei, imei.trim());
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Device::getStatus, status.trim().toUpperCase());
        }

        queryWrapper.orderByDesc(Device::getUpdatedAt);

        return deviceMapper.selectPage(page, queryWrapper);
    }

    @Override
    public Optional<Device> findById(Long id) {
        return Optional.ofNullable(deviceMapper.selectById(id));
    }

    @Override
    public Optional<Device> findByImei(String imei) {
        return Optional.ofNullable(deviceMapper.selectByImei(imei));
    }

    @Override
    public Device create(Device device) {
        deviceMapper.insert(device);
        return device;
    }

    @Override
    public Device updateById(Device device) {
        deviceMapper.updateById(device);
        return device;
    }

    @Override
    public long countByImeiExcludingId(String imei, Long excludeId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<Device>()
                .eq(Device::getImei, imei)
                .isNull(Device::getDeletedAt);

        if (excludeId != null) {
            queryWrapper.ne(Device::getId, excludeId);
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
                deviceMapper.insert(device);
            }
        }
    }

    @Override
    public Page<Device> pageByImportBatchId(Page<Device> page, Long importBatchId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<Device>()
                .isNull(Device::getDeletedAt);

        if (importBatchId != null) {
            queryWrapper.eq(Device::getImportBatchId, importBatchId);
        }

        queryWrapper.orderByDesc(Device::getCreatedAt);

        return deviceMapper.selectPage(page, queryWrapper);
    }

    @Override
    public List<Device> findAllByImportBatchId(Long importBatchId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<Device>()
                .isNull(Device::getDeletedAt)
                .eq(Device::getImportBatchId, importBatchId);
        return deviceMapper.selectList(queryWrapper);
    }

    @Override
    public List<Device> findByImeis(List<String> imeis) {
        if (imeis == null || imeis.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<Device>()
                .isNull(Device::getDeletedAt)
                .in(Device::getImei, imeis);
        return deviceMapper.selectList(queryWrapper);
    }

    @Override
    public List<Device> findByConditions(Long productId, String imeiKeyword, String status, Long importBatchId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<Device>()
                .isNull(Device::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(Device::getProductId, productId);
        }
        if (StringUtils.hasText(imeiKeyword)) {
            queryWrapper.like(Device::getImei, imeiKeyword.trim());
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Device::getStatus, status.trim().toUpperCase());
        }
        if (importBatchId != null) {
            queryWrapper.eq(Device::getImportBatchId, importBatchId);
        }

        queryWrapper.orderByDesc(Device::getUpdatedAt);

        return deviceMapper.selectList(queryWrapper);
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

            LambdaUpdateWrapper<Device> updateWrapper = new LambdaUpdateWrapper<Device>()
                    .set(Device::getImportBatchId, batchId)
                    .in(Device::getId, batchIds);

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
}
