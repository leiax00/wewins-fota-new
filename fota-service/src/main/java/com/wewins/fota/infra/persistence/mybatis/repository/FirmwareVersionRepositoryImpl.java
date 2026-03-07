package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.infra.persistence.converter.FirmwareVersionConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.FirmwareVersionMapper;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FirmwareVersionRepository 的 MyBatis 实现。
 */
@Repository
public class FirmwareVersionRepositoryImpl implements FirmwareVersionRepository {

    private static final Logger log = LoggerFactory.getLogger(FirmwareVersionRepositoryImpl.class);

    private final FirmwareVersionMapper firmwareVersionMapper;

    private final FirmwareCacheRepository firmwareCacheRepository;

    private final FirmwareVersionConverter firmwareVersionConverter;

    public FirmwareVersionRepositoryImpl(
            FirmwareVersionMapper firmwareVersionMapper,
            FirmwareCacheRepository firmwareCacheRepository,
            FirmwareVersionConverter firmwareVersionConverter) {
        this.firmwareVersionMapper = firmwareVersionMapper;
        this.firmwareCacheRepository = firmwareCacheRepository;
        this.firmwareVersionConverter = firmwareVersionConverter;
    }

    @Override
    public Optional<FirmwareVersion> findById(Long id) {
        Optional<FirmwareVersion> cached = firmwareCacheRepository.findById(id);
        if (cached.isPresent()) {
            log.debug("固件缓存命中: versionId={}", id);
            return cached;
        }

        FirmwareVersionPO firmwarePo = firmwareVersionMapper.selectById(id);
        FirmwareVersion firmware = firmwareVersionConverter.toDomain(firmwarePo);
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
        List<FirmwareVersionPO> firmwareVersions = firmwareVersionMapper.selectByIds(ids);
        return firmwareVersionConverter.toDomainList(firmwareVersions);
    }

    @Override
    public Map<Long, String> findVersionNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        List<FirmwareVersionPO> versions = firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersionPO>()
                        .in(FirmwareVersionPO::getId, ids)
                        .select(FirmwareVersionPO::getId, FirmwareVersionPO::getVersion, FirmwareVersionPO::getInternalVersion)
        );

        return versions.stream()
                .collect(Collectors.toMap(
                        FirmwareVersionPO::getId,
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
        List<FirmwareVersionPO> pos = firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersionPO>()
                        .eq(FirmwareVersionPO::getProductId, productId)
                        .isNull(FirmwareVersionPO::getDeletedAt)
                        .orderByDesc(FirmwareVersionPO::getVersion)
        );
        return firmwareVersionConverter.toDomainList(pos);
    }

    @Override
    public List<FirmwareVersion> findByProductIdAndVersion(Long productId, String version) {
        List<FirmwareVersionPO> pos = firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersionPO>()
                        .eq(FirmwareVersionPO::getProductId, productId)
                        .eq(FirmwareVersionPO::getVersion, version)
                        .isNull(FirmwareVersionPO::getDeletedAt)
        );
        return firmwareVersionConverter.toDomainList(pos);
    }

    @Override
    public Optional<FirmwareVersion> findByVersionNumberAndProductId(String versionNumber, Long productId) {
        if (versionNumber == null || productId == null) {
            return Optional.empty();
        }

        FirmwareVersionPO po = firmwareVersionMapper.selectOne(
                new LambdaQueryWrapper<FirmwareVersionPO>()
                        .eq(FirmwareVersionPO::getProductId, productId)
                        .eq(FirmwareVersionPO::getVersion, versionNumber)
                        .isNull(FirmwareVersionPO::getDeletedAt)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(firmwareVersionConverter.toDomain(po));
    }

    @Override
    public Optional<FirmwareVersion> findByUniqueKey(
            String versionNumber,
            String internalVersion,
            Long productId) {
        if (versionNumber == null || productId == null) {
            return Optional.empty();
        }

        LambdaQueryWrapper<FirmwareVersionPO> wrapper = new LambdaQueryWrapper<FirmwareVersionPO>()
                .eq(FirmwareVersionPO::getProductId, productId)
                .eq(FirmwareVersionPO::getVersion, versionNumber)
                .isNull(FirmwareVersionPO::getDeletedAt)
                .last("LIMIT 1");

        // 如果提供了 internalVersion，则作为查询条件
        if (StringUtils.hasText(internalVersion)) {
            wrapper.eq(FirmwareVersionPO::getInternalVersion, internalVersion);
        }

        return Optional.ofNullable(firmwareVersionConverter.toDomain(firmwareVersionMapper.selectOne(wrapper)));
    }

    @Override
    public Page<FirmwareVersion> pageFirmwareVersions(Page<FirmwareVersion> page, Long productId, String version) {
        LambdaQueryWrapper<FirmwareVersionPO> queryWrapper = new LambdaQueryWrapper<FirmwareVersionPO>()
                .isNull(FirmwareVersionPO::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(FirmwareVersionPO::getProductId, productId);
        }

        if (StringUtils.hasText(version)) {
            queryWrapper.like(FirmwareVersionPO::getVersion, version);
        }

        queryWrapper.orderByDesc(FirmwareVersionPO::getUpdatedAt);

        Page<FirmwareVersionPO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<FirmwareVersionPO> queried = firmwareVersionMapper.selectPage(poPage, queryWrapper);

        Page<FirmwareVersion> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(firmwareVersionConverter.toDomainList(queried.getRecords()));
        return result;
    }

    @Override
    public FirmwareVersion create(FirmwareVersion firmwareVersion) {
        FirmwareVersionPO po = firmwareVersionConverter.toPo(firmwareVersion);
        firmwareVersionMapper.insert(po);
        return firmwareVersionConverter.toDomain(po);
    }

    @Override
    public FirmwareVersion updateById(FirmwareVersion firmwareVersion) {
        FirmwareVersionPO po = firmwareVersionConverter.toPo(firmwareVersion);
        firmwareVersionMapper.updateById(po);
        return firmwareVersionConverter.toDomain(po);
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

        LambdaQueryWrapper<FirmwareVersionPO> wrapper = new LambdaQueryWrapper<FirmwareVersionPO>()
                .eq(FirmwareVersionPO::getProductId, productId)
                .eq(FirmwareVersionPO::getVersion, version)
                .isNull(FirmwareVersionPO::getDeletedAt);

        // 如果提供了 internalVersion，检查 product + version + internalVersion 组合
        if (StringUtils.hasText(internalVersion)) {
            wrapper.eq(FirmwareVersionPO::getInternalVersion, internalVersion);
        }

        Long count = firmwareVersionMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

}
