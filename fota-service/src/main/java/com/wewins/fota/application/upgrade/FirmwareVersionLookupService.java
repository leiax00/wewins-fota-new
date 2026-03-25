package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.upgrade.dto.CheckContext;
import com.wewins.fota.common.util.TagMapUtils;
import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionLookupCacheRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 固件版本查找服务
 * <p>
 * 负责根据 product + version + internalVersion 查找固件版本候选集，
 * 然后通过设备 tags 命中具体固件
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareVersionLookupService {

    private final FirmwareVersionRepository firmwareVersionRepository;
    private final FirmwareCacheRepository firmwareCacheRepository;
    private final FirmwareVersionLookupCacheRepository firmwareVersionLookupCacheRepository;
    private final FirmwareTagSchemaProvider firmwareTagSchemaProvider;

    public List<Long> findCandidateVersionIds(String version, String internalVersion, Long productId) {
        if (!StringUtils.hasText(version) || !StringUtils.hasText(internalVersion) || productId == null) {
            log.debug("查找固件版本候选失败：缺少必要参数, version={}, internalVersion={}, productId={}",
                    version, internalVersion, productId);
            return List.of();
        }

        List<Long> versionIds = findCandidateVersionIdsByExactKey(version, internalVersion, productId);
        if (!versionIds.isEmpty()) {
            log.debug("匹配到固件版本候选: version={}, internalVersion={}, productId={}, versionIds={}",
                    version, internalVersion, productId, versionIds);
        } else {
            log.debug("未找到匹配的固件版本候选: version={}, internalVersion={}, productId={}",
                    version, internalVersion, productId);
        }
        return versionIds;
    }

    public Optional<FirmwareVersion> findMatchedFirmwareVersion(CheckContext ctx) {
        Long productId = ctx.productId();
        String version = ctx.version();
        String internalVersion = ctx.internalVersion();

        List<FirmwareVersion> candidateFirmwares = loadCandidateFirmwares(version, internalVersion, productId);
        if (candidateFirmwares.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> deviceTags = new HashMap<>();
        if (ctx.getDevice().getTags() != null) {
            deviceTags.putAll(ctx.getDevice().getTags());
        }
        String hardwareVersion = ctx.getRequest().getHardwareVersion();
        if (StringUtils.hasText(hardwareVersion)) {
            deviceTags.put("hwVersion", hardwareVersion);
        }
        return candidateFirmwares.stream()
                .filter(firmware -> firmware != null && matchesFirmwareTags(firmware.getTags(), deviceTags))
                .min(Comparator.comparingInt(
                    (FirmwareVersion firmware) -> firmware.getTags() == null ? 0 : firmware.getTags().size()
                ).reversed().thenComparing(FirmwareVersion::getId, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private List<Long> findCandidateVersionIdsByExactKey(String version, String internalVersion, Long productId) {
        CacheLookupResult<List<Long>> cached =
                firmwareVersionLookupCacheRepository.get(productId, version, internalVersion);
        if (cached.hit()) {
            return cached.value() != null ? cached.value() : List.of();
        }

        return queryAndCacheCandidateFirmwares(version, internalVersion, productId).stream()
                .map(FirmwareVersion::getId)
                .toList();
    }

    private List<FirmwareVersion> loadCandidateFirmwares(String version, String internalVersion, Long productId) {
        if (!StringUtils.hasText(version) || !StringUtils.hasText(internalVersion) || productId == null) {
            return List.of();
        }

        CacheLookupResult<List<Long>> cached =
                firmwareVersionLookupCacheRepository.get(productId, version, internalVersion);
        if (cached.hit()) {
            List<Long> cachedIds = cached.value();
            if (cachedIds == null || cachedIds.isEmpty()) {
                return List.of();
            }
            return cachedIds.stream()
                    .map(firmwareVersionRepository::findById)
                    .flatMap(Optional::stream)
                    .toList();
        }

        return queryAndCacheCandidateFirmwares(version, internalVersion, productId);
    }

    private List<FirmwareVersion> queryAndCacheCandidateFirmwares(String version, String internalVersion, Long productId) {
        List<FirmwareVersion> firmwares = firmwareVersionRepository
                .findByVersionAndInternalVersionAndProductId(version, internalVersion, productId);
        if (firmwares.isEmpty()) {
            firmwareVersionLookupCacheRepository.putNotFound(productId, version, internalVersion);
            return List.of();
        }

        List<Long> versionIds = firmwares.stream()
                .map(FirmwareVersion::getId)
                .toList();
        firmwareVersionLookupCacheRepository.put(productId, version, internalVersion, versionIds);
        firmwares.forEach(firmwareCacheRepository::cacheFirmware);
        return firmwares;
    }

    /**
     * 批量查找固件版本 ID
     * <p>
     * 用于多个版本查找的场景，如策略中的 sourceVersions
     * </p>
     *
     * @param versionTagPairs 版本号和标签对列表
     * @param productId       产品 ID
     * @return 版本号到版本 ID 的映射
     */
    public java.util.Map<String, List<Long>> findVersionIds(
            java.util.List<VersionTagPair> versionTagPairs,
            Long productId) {
        if (versionTagPairs == null || versionTagPairs.isEmpty()) {
            return java.util.Map.of();
        }

        java.util.Map<String, List<Long>> result = new java.util.HashMap<>();
        for (VersionTagPair pair : versionTagPairs) {
            List<Long> versionIds = findCandidateVersionIds(pair.version, pair.tag, productId);
            if (!versionIds.isEmpty()) {
                result.put(pair.version, versionIds);
            }
        }
        return result;
    }

    private boolean matchesFirmwareTags(Map<String, String> firmwareTags, Map<String, String> deviceTags) {
        Set<String> schemaKeys = firmwareTagSchemaProvider.getTagKeys();
        // 表示不启用标签匹配
        if (schemaKeys.isEmpty()) {
            return true;
        }

        return TagMapUtils.equalsOnKeys(firmwareTags, deviceTags, schemaKeys);
    }

    /**
     * 版本号和标签对
     */
    public record VersionTagPair(String version, String tag) {
        public VersionTagPair {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version 不能为空");
            }
        }

        /**
         * 创建仅有 version 的对
         */
        public static VersionTagPair of(String version) {
            return new VersionTagPair(version, null);
        }

        /**
         * 创建 version + tag 的对
         */
        public static VersionTagPair of(String version, String tag) {
            return new VersionTagPair(version, tag);
        }
    }
}
