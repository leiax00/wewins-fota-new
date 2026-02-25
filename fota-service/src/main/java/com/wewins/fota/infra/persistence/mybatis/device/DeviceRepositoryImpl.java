package com.wewins.fota.infra.persistence.mybatis.device;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.infra.persistence.mybatis.device.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
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
}
