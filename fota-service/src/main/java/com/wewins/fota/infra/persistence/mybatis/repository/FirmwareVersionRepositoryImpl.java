package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.infra.persistence.converter.FirmwareVersionConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.FirmwareVersionMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.FirmwareVersionTagMapper;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionPO;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionTagPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
public class FirmwareVersionRepositoryImpl implements FirmwareVersionRepository {

    private static final Logger log = LoggerFactory.getLogger(FirmwareVersionRepositoryImpl.class);

    private final FirmwareVersionMapper firmwareVersionMapper;
    private final FirmwareCacheRepository firmwareCacheRepository;
    private final FirmwareVersionConverter firmwareVersionConverter;
    private final FirmwareVersionTagMapper firmwareVersionTagMapper;

    public FirmwareVersionRepositoryImpl(
            FirmwareVersionMapper firmwareVersionMapper,
            FirmwareCacheRepository firmwareCacheRepository,
            FirmwareVersionConverter firmwareVersionConverter,
            FirmwareVersionTagMapper firmwareVersionTagMapper) {
        this.firmwareVersionMapper = firmwareVersionMapper;
        this.firmwareCacheRepository = firmwareCacheRepository;
        this.firmwareVersionConverter = firmwareVersionConverter;
        this.firmwareVersionTagMapper = firmwareVersionTagMapper;
    }

    @Override
    public Optional<FirmwareVersion> findById(Long id) {
        Optional<FirmwareVersion> cached = firmwareCacheRepository.findById(id);
        if (cached.isPresent()) {
            log.debug("固件缓存命中: versionId={}", id);
            return cached;
        }

        FirmwareVersionPO firmwarePo = firmwareVersionMapper.selectById(id);
        FirmwareVersion firmware = enrichFirmware(firmwareVersionConverter.toDomain(firmwarePo));
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
        return enrichFirmwareVersions(firmwareVersionConverter.toDomainList(firmwareVersions));
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
                        .eq(FirmwareVersionPO::getDeleted, 0)
                        .orderByDesc(FirmwareVersionPO::getVersion)
        );
        return enrichFirmwareVersions(firmwareVersionConverter.toDomainList(pos));
    }

    @Override
    public List<FirmwareVersion> findByProductIdAndVersion(Long productId, String version) {
        List<FirmwareVersionPO> pos = firmwareVersionMapper.selectList(
                new LambdaQueryWrapper<FirmwareVersionPO>()
                        .eq(FirmwareVersionPO::getProductId, productId)
                        .eq(FirmwareVersionPO::getVersion, version)
                        .eq(FirmwareVersionPO::getDeleted, 0)
        );
        return enrichFirmwareVersions(firmwareVersionConverter.toDomainList(pos));
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
                        .eq(FirmwareVersionPO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(enrichFirmware(firmwareVersionConverter.toDomain(po)));
    }

    @Override
    public Optional<FirmwareVersion> findByUniqueKey(String versionNumber, String internalVersion, Long productId) {
        if (versionNumber == null || productId == null) {
            return Optional.empty();
        }

        LambdaQueryWrapper<FirmwareVersionPO> wrapper = new LambdaQueryWrapper<FirmwareVersionPO>()
                .eq(FirmwareVersionPO::getProductId, productId)
                .eq(FirmwareVersionPO::getVersion, versionNumber)
                .eq(FirmwareVersionPO::getDeleted, 0)
                .last("LIMIT 1");

        if (StringUtils.hasText(internalVersion)) {
            wrapper.eq(FirmwareVersionPO::getInternalVersion, internalVersion);
        }

        return Optional.ofNullable(enrichFirmware(firmwareVersionConverter.toDomain(firmwareVersionMapper.selectOne(wrapper))));
    }

    @Override
    public Page<FirmwareVersion> pageFirmwareVersions(Page<FirmwareVersion> page, Long productId, String version) {
        LambdaQueryWrapper<FirmwareVersionPO> queryWrapper = new LambdaQueryWrapper<FirmwareVersionPO>()
                .eq(FirmwareVersionPO::getDeleted, 0);

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
        result.setRecords(enrichFirmwareVersions(firmwareVersionConverter.toDomainList(queried.getRecords())));
        return result;
    }

    @Override
    public FirmwareVersion create(FirmwareVersion firmwareVersion) {
        FirmwareVersionPO po = firmwareVersionConverter.toPo(firmwareVersion);
        firmwareVersionMapper.insert(po);
        firmwareVersion.setId(po.getId());
        firmwareVersion.setCreatedAt(po.getCreatedAt());
        firmwareVersion.setCreatedBy(po.getCreatedBy());
        firmwareVersion.setUpdatedAt(po.getUpdatedAt());
        firmwareVersion.setUpdatedBy(po.getUpdatedBy());
        syncTags(firmwareVersion);
        firmwareCacheRepository.evict(firmwareVersion.getId());
        return enrichFirmware(firmwareVersion);
    }

    @Override
    public FirmwareVersion updateById(FirmwareVersion firmwareVersion) {
        FirmwareVersionPO po = firmwareVersionConverter.toPo(firmwareVersion);
        firmwareVersionMapper.updateById(po);
        firmwareVersion.setUpdatedAt(po.getUpdatedAt());
        firmwareVersion.setUpdatedBy(po.getUpdatedBy());
        syncTags(firmwareVersion);
        firmwareCacheRepository.evict(firmwareVersion.getId());
        return enrichFirmware(firmwareVersion);
    }

    @Override
    public boolean deleteById(Long id) {
        boolean deleted = firmwareVersionMapper.deleteById(id) > 0;
        if (deleted) {
            firmwareCacheRepository.evict(id);
        }
        return deleted;
    }

    @Override
    public boolean existsByUnique(FirmwareVersion firmwareVersion) {
        if (firmwareVersion == null
                || firmwareVersion.getProductId() == null
                || !StringUtils.hasText(firmwareVersion.getVersion())) {
            return false;
        }

        return findByProductIdAndVersion(firmwareVersion.getProductId(), firmwareVersion.getVersion()).stream()
                .filter(existing -> !Objects.equals(existing.getId(), firmwareVersion.getId()))
                .anyMatch(existing -> Objects.equals(existing.getInternalVersion(), firmwareVersion.getInternalVersion())
                        && Objects.equals(normalizeTags(existing.getTags()), normalizeTags(firmwareVersion.getTags())));
    }

    private List<FirmwareVersion> enrichFirmwareVersions(List<FirmwareVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return versions;
        }
        List<Long> ids = versions.stream().map(FirmwareVersion::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            return versions;
        }
        Map<Long, Map<String, String>> tagsByVersionId = firmwareVersionTagMapper.selectByVersionIds(ids).stream()
                .collect(Collectors.groupingBy(
                        FirmwareVersionTagPO::getFirmwareVersionId,
                        LinkedHashMap::new,
                        Collectors.toMap(FirmwareVersionTagPO::getTagKey, FirmwareVersionTagPO::getTagValue, (a, b) -> b, LinkedHashMap::new)
                ));
        versions.forEach(version -> {
            Map<String, String> tags = tagsByVersionId.get(version.getId());
            if (tags != null && !tags.isEmpty()) {
                version.setTags(tags);
            }
        });
        return versions;
    }

    private FirmwareVersion enrichFirmware(FirmwareVersion version) {
        if (version == null || version.getId() == null) {
            return version;
        }
        return enrichFirmwareVersions(List.of(version)).getFirst();
    }

    private void syncTags(FirmwareVersion firmwareVersion) {
        if (firmwareVersion == null || firmwareVersion.getId() == null) {
            return;
        }
        firmwareVersionTagMapper.deleteByVersionIds(List.of(firmwareVersion.getId()));
        if (firmwareVersion.getTags() == null || firmwareVersion.getTags().isEmpty()) {
            return;
        }
        List<FirmwareVersionTagPO> rows = firmwareVersion.getTags().entrySet().stream()
                .map(entry -> {
                    FirmwareVersionTagPO row = new FirmwareVersionTagPO();
                    row.setFirmwareVersionId(firmwareVersion.getId());
                    row.setTagKey(entry.getKey());
                    row.setTagValue(entry.getValue());
                    return row;
                })
                .toList();
        firmwareVersionTagMapper.batchInsert(rows);
    }

    private Map<String, String> normalizeTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        return tags;
    }
}
