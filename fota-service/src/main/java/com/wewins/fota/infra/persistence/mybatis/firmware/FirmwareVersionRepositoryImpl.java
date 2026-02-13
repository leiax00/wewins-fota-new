package com.wewins.fota.infra.persistence.mybatis.firmware;

import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.infra.persistence.mybatis.firmware.mapper.FirmwareVersionMapper;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * FirmwareVersionRepository 的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class FirmwareVersionRepositoryImpl implements FirmwareVersionRepository {

    private final FirmwareVersionMapper firmwareVersionMapper;

    @Override
    public Optional<FirmwareVersion> findById(Long id) {
        return Optional.ofNullable(firmwareVersionMapper.selectById(id));
    }
}
