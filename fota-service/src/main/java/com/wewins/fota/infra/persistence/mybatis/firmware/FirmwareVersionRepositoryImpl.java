package com.wewins.fota.infra.persistence.mybatis.firmware;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.firmware.cache.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.infra.persistence.mybatis.firmware.mapper.FirmwareVersionMapper;
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

    private final FirmwareCacheRepository firmwareCacheRepository;

    @Override
    public Optional<FirmwareVersion> findById(Long id) {
        Optional<FirmwareVersion> cached = firmwareCacheRepository.findById(id);
        if (cached.isPresent()) {
            log.debug("固件缓存命中: versionId={}", id);
            return cached;
        }

        FirmwareVersion firmware = firmwareVersionMapper.selectById(id);
        if (firmware != null) {
            firmwareCacheRepository.cacheFirmware(firmware);
            log.debug("固件缓存已写入: versionId={}", id);
        }

        return Optional.ofNullable(firmware);
    }

    @Override
    public List<FirmwareVersion> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return firmwareVersionMapper.selectByIds(ids);
    }

    @Override
    public Map<Long, String> findVersionNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        List<FirmwareVersion> versions = firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersion>()
                        .in(FirmwareVersion::getId, ids)
                        .select(FirmwareVersion::getId, FirmwareVersion::getVersion, FirmwareVersion::getInternalVersion)
        );

        return versions.stream()
                .collect(Collectors.toMap(
                        FirmwareVersion::getId,
                        v -> formatVersionLabel(v.getVersion(), v.getInternalVersion())
                ));
    }

    /**
     * 格式化版本显示：版本号 (内部版本号) 或仅版本号
     */
    private String formatVersionLabel(String version, String internalVersion) {
        if (internalVersion != null && !internalVersion.isBlank()) {
            return String.format("%s (%s)", version, internalVersion);
        }
        return version;
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
    public Optional<FirmwareVersion> findByUniqueKey(
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

    @Override
    public boolean existsByUnique(Long productId, String version, String internalVersion) {
        if (productId == null || version == null || version.isBlank()) {
            return false;
        }

        LambdaQueryWrapper<FirmwareVersion> wrapper = new LambdaQueryWrapper<FirmwareVersion>()
                .eq(FirmwareVersion::getProductId, productId)
                .eq(FirmwareVersion::getVersion, version)
                .isNull(FirmwareVersion::getDeletedAt);

        // 如果提供了 internalVersion，检查 product + version + internalVersion 组合
        if (StringUtils.hasText(internalVersion)) {
            wrapper.eq(FirmwareVersion::getInternalVersion, internalVersion);
        }

        Long count = firmwareVersionMapper.selectCount(wrapper);
        return count != null && count > 0;
    }
}
