package com.wewins.fota.application.upgrade;

import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 固件版本查找服务
 * <p>
 * 负责根据版本号和内部版本号（tag）查找固件版本
 * </p>
 * <p>
 * 查找优先级：
 * </p>
 * <ol>
 *   <li>version + tag 组合查找（精确匹配）</li>
 *   <li>仅 version 查找（向后兼容）</li>
 * </ol>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareVersionLookupService {

    private final FirmwareVersionRepository firmwareVersionRepository;

    /**
     * 查找固件版本 ID
     * <p>
     * 查找优先级：
     * </p>
     * <ol>
     *   <li>如果提供了 tag，使用 version + tag 组合精确查找</li>
 *   *   <li>如果组合查找失败或未提供 tag，降级到仅 version 查找</li>
     * </ol>
     * <p>
     * 这种设计是为了解决历史问题：version 号可能在不同的构建中重复，
     * 需要通过 tag（内部版本号）来精确区分。
     * </p>
     *
     * @param version    版本号（如 "Mobile.Router.B03"）
     * @param tag        内部版本号（如 "ASR_YEMEN_M476_V11_B03_Build02"），可选
     * @param productId  产品 ID
     * @return 固件版本 ID，如果未找到返回 null
     */
    public Long findVersionId(String version, String tag, Long productId) {
        if (version == null || productId == null) {
            log.debug("查找固件版本 ID 失败：缺少必要参数, version={}, productId={}", version, productId);
            return null;
        }

        Long versionId = null;

        // 1. 优先：version + tag 组合查找（精确匹配）
        if (StringUtils.hasText(tag)) {
            versionId = findByVersionAndTag(version, tag, productId);
            if (versionId != null) {
                log.debug("精确匹配固件版本: version={}, tag={}, productId={}, versionId={}",
                        version, tag, productId, versionId);
                return versionId;
            }
            log.debug("version + tag 组合查找未找到结果，降级到 version 查找: version={}, tag={}, productId={}",
                    version, tag, productId);
        }

        // 2. 降级：仅 version 查找（向后兼容）
        versionId = findByVersionOnly(version, productId);
        if (versionId != null) {
            log.debug("通过 version 查找到固件版本: version={}, productId={}, versionId={}",
                    version, productId, versionId);
        } else {
            log.debug("未找到匹配的固件版本: version={}, tag={}, productId={}",
                    version, tag, productId);
        }

        return versionId;
    }

    /**
     * 通过 version + tag 组合查找固件版本 ID
     *
     * @param version   版本号
     * @param tag       内部版本号
     * @param productId 产品 ID
     * @return 固件版本 ID，如果未找到返回 null
     */
    private Long findByVersionAndTag(String version, String tag, Long productId) {
        Optional<FirmwareVersion> firmware = firmwareVersionRepository
                .findByVersionNumberAndInternalVersionAndProductId(version, tag, productId);
        return firmware.map(FirmwareVersion::getId).orElse(null);
    }

    /**
     * 仅通过 version 查找固件版本 ID
     *
     * @param version   版本号
     * @param productId 产品 ID
     * @return 固件版本 ID，如果未找到返回 null
     */
    private Long findByVersionOnly(String version, Long productId) {
        Optional<FirmwareVersion> firmware = firmwareVersionRepository
                .findByVersionNumberAndProductId(version, productId);
        return firmware.map(FirmwareVersion::getId).orElse(null);
    }

    /**
     * 查找固件版本实体
     * <p>
     * 返回完整的固件版本实体，包含所有元数据
     * </p>
     *
     * @param version   版本号
     * @param tag       内部版本号，可选
     * @param productId 产品 ID
     * @return 固件版本实体，如果未找到返回 null
     */
    public FirmwareVersion findFirmwareVersion(String version, String tag, Long productId) {
        if (version == null || productId == null) {
            return null;
        }

        // 1. 优先：version + tag 组合查找
        if (StringUtils.hasText(tag)) {
            Optional<FirmwareVersion> firmware = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(version, tag, productId);
            if (firmware.isPresent()) {
                return firmware.get();
            }
        }

        // 2. 降级：仅 version 查找
        return firmwareVersionRepository
                .findByVersionNumberAndProductId(version, productId)
                .orElse(null);
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
    public java.util.Map<String, Long> findVersionIds(
            java.util.List<VersionTagPair> versionTagPairs,
            Long productId) {
        if (versionTagPairs == null || versionTagPairs.isEmpty()) {
            return java.util.Map.of();
        }

        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (VersionTagPair pair : versionTagPairs) {
            Long versionId = findVersionId(pair.getVersion(), pair.getTag(), productId);
            if (versionId != null) {
                // 使用 version 作为 key，因为 version 是业务主键
                result.put(pair.getVersion(), versionId);
            }
        }
        return result;
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
