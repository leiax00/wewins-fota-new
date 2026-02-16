package com.wewins.fota.infra.persistence.mybatis.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.infra.persistence.mybatis.device.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public Optional<Device> findById(Long id) {
        return Optional.ofNullable(deviceMapper.selectById(id));
    }

    @Override
    public Optional<Device> findByImei(String imei) {
        return Optional.ofNullable(deviceMapper.selectByImei(imei));
    }

    @Override
    public boolean softDeleteById(Long id) {
        return deviceMapper.deleteById(id) > 0;
    }
}
