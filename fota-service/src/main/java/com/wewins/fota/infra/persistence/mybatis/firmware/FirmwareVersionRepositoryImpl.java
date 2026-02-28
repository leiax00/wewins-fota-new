package com.wewins.fota.infra.persistence.mybatis.firmware;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.infra.persistence.mybatis.firmware.mapper.FirmwareVersionMapper;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FirmwareVersionRepository 的 MyBatis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FirmwareVersionRepositoryImpl implements FirmwareVersionRepository {

    private final FirmwareVersionMapper firmwareVersionMapper;

    @Override
    public Optional<FirmwareVersion> findById(Long id) {
        return Optional.ofNullable(firmwareVersionMapper.selectById(id));
    }

    @Override
    public List<FirmwareVersion> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return firmwareVersionMapper.selectBatchIds(ids);
    }

    @Override
    public Map<Long, String> findVersionNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        List<FirmwareVersion> versions = firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersion>()
                        .in(FirmwareVersion::getId, ids)
                        .select(FirmwareVersion::getId, FirmwareVersion::getVersion)
        );

        return versions.stream()
                .collect(Collectors.toMap(
                        FirmwareVersion::getId,
                        FirmwareVersion::getVersion
                ));
    }

    @Override
    public List<FirmwareVersion> findByProductIdOrderByVersionDesc(Long productId) {
        return firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersion>()
                        .eq(FirmwareVersion::getProductId, productId)
                        .isNull(FirmwareVersion::getDeletedAt)
                        .orderByDesc(FirmwareVersion::getVersion)
        );
    }

    @Override
    public List<FirmwareVersion> findByProductIdAndVersion(Long productId, String version) {
        return firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersion>()
                        .eq(FirmwareVersion::getProductId, productId)
                        .eq(FirmwareVersion::getVersion, version)
                        .isNull(FirmwareVersion::getDeletedAt)
        );
    }

    @Override
    public Optional<FirmwareVersion> findByVersionNumberAndProductId(String versionNumber, Long productId) {
        if (versionNumber == null || productId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                firmwareVersionMapper.selectOne(
                        new LambdaQueryWrapper<FirmwareVersion>()
                                .eq(FirmwareVersion::getProductId, productId)
                                .eq(FirmwareVersion::getVersion, versionNumber)
                                .isNull(FirmwareVersion::getDeletedAt)
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public Optional<FirmwareVersion> findByVersionNumberAndInternalVersionAndProductId(
            String versionNumber,
            String internalVersion,
            Long productId) {
        if (versionNumber == null || productId == null) {
            return Optional.empty();
        }

        LambdaQueryWrapper<FirmwareVersion> wrapper = new LambdaQueryWrapper<FirmwareVersion>()
                .eq(FirmwareVersion::getProductId, productId)
                .eq(FirmwareVersion::getVersion, versionNumber)
                .isNull(FirmwareVersion::getDeletedAt)
                .last("LIMIT 1");

        // 如果提供了 internalVersion，则作为查询条件
        if (StringUtils.hasText(internalVersion)) {
            wrapper.eq(FirmwareVersion::getInternalVersion, internalVersion);
        }

        return Optional.ofNullable(firmwareVersionMapper.selectOne(wrapper));
    }

    @Override
    public Page<FirmwareVersion> pageFirmwareVersions(Page<FirmwareVersion> page, Long productId, String version) {
        LambdaQueryWrapper<FirmwareVersion> queryWrapper = new LambdaQueryWrapper<FirmwareVersion>()
                .isNull(FirmwareVersion::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(FirmwareVersion::getProductId, productId);
        }

        if (StringUtils.hasText(version)) {
            queryWrapper.like(FirmwareVersion::getVersion, version);
        }

        queryWrapper.orderByDesc(FirmwareVersion::getUpdatedAt);

        return firmwareVersionMapper.selectPage(page, queryWrapper);
    }

    @Override
    public FirmwareVersion create(FirmwareVersion firmwareVersion) {
        firmwareVersionMapper.insert(firmwareVersion);
        return firmwareVersion;
    }

    @Override
    public FirmwareVersion updateById(FirmwareVersion firmwareVersion) {
        firmwareVersionMapper.updateById(firmwareVersion);
        return firmwareVersion;
    }

    @Override
    public boolean deleteById(Long id) {
        return firmwareVersionMapper.deleteById(id) > 0;
    }
}
