package com.wewins.fota.infra.persistence.mybatis.device;

import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.infra.persistence.mybatis.device.mapper.DeviceMapper;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * DeviceRepository 的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceMapper deviceMapper;

    @Override
    public Optional<Device> findByImei(String imei) {
        return Optional.ofNullable(deviceMapper.selectByImei(imei));
    }
}
