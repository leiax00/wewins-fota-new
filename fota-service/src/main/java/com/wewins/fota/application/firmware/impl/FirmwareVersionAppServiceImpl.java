package com.wewins.fota.application.firmware.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.firmware.FirmwareVersionAppService;
import com.wewins.fota.application.firmware.dto.FirmwareVersionPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.infra.cache.event.ChangeType;
import com.wewins.fota.infra.cache.event.FirmwareChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 固件版本应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareVersionAppServiceImpl implements FirmwareVersionAppService {

    private final FirmwareVersionRepository firmwareVersionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Page<FirmwareVersion> pageFirmwareVersions(FirmwareVersionPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new FirmwareVersionPageReqDTO();
        }
        reqDTO.validate();

        Page<FirmwareVersion> page = new Page<>(reqDTO.getPage(), reqDTO.getSize());

        if (log.isDebugEnabled()) {
            log.debug("分页查询固件版本: productId={}, version={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), reqDTO.getPage(), reqDTO.getSize());
        }

        return firmwareVersionRepository.pageFirmwareVersions(page, reqDTO.getProductId(), reqDTO.getVersion());
    }

    @Override
    public List<FirmwareVersion> listByProductId(Long productId) {
        if (productId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("查询产品固件版本列表: productId={}", productId);
        }

        return firmwareVersionRepository.findByProductIdOrderByVersionDesc(productId);
    }

    @Override
    public FirmwareVersion getById(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return firmwareVersionRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.FIRMWARE_VERSION_NOT_FOUND));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FirmwareVersion createFirmwareVersion(FirmwareVersion firmwareVersion) {
        if (firmwareVersion == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "固件版本信息不能为空");
        }

        if (firmwareVersion.getProductId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("创建固件版本: productId={}, version={}, internalVersion={}",
                    firmwareVersion.getProductId(), firmwareVersion.getVersion(), firmwareVersion.getInternalVersion());
        }

        // 检查 product + version + internalVersion 组合是否已存在
        boolean exists = firmwareVersionRepository.existsByUnique(
                firmwareVersion.getProductId(),
                firmwareVersion.getVersion(),
                firmwareVersion.getInternalVersion()
        );
        if (exists) {
            throw new BizException(ErrorCode.FIRMWARE_VERSION_EXISTS);
        }

        firmwareVersionRepository.create(firmwareVersion);
        eventPublisher.publishEvent(new FirmwareChangedEvent(
                this,
                firmwareVersion.getProductId(),
                firmwareVersion.getId(),
                firmwareVersion.getVersion(),
                firmwareVersion.getInternalVersion(),
                ChangeType.CREATED));

        log.info("固件版本创建成功: firmwareVersionId={}, productId={}, version={}, internalVersion={}",
                firmwareVersion.getId(), firmwareVersion.getProductId(), firmwareVersion.getVersion(), firmwareVersion.getInternalVersion());
        return firmwareVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FirmwareVersion updateFirmwareVersion(FirmwareVersion firmwareVersion) {
        if (firmwareVersion == null || firmwareVersion.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "固件版本 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新固件版本: firmwareVersionId={}", firmwareVersion.getId());
        }

        // 检查固件版本是否存在
        FirmwareVersion existingVersion = getById(firmwareVersion.getId());

        if (existingVersion == null) {
            throw new BizException(ErrorCode.FIRMWARE_VERSION_NOT_FOUND);
        }

        Long productId = firmwareVersion.getProductId();
        String version = firmwareVersion.getVersion();
        String internalVersion = firmwareVersion.getInternalVersion();

        if (!Objects.equals(productId, existingVersion.getProductId()) ||
                !Objects.equals(version, existingVersion.getVersion()) ||
                !Objects.equals(internalVersion, existingVersion.getInternalVersion())
        ) {
            throw new BizException(ErrorCode.FIRMWARE_VERSION_UPDATE_CHANGE_UNIQUE);
        }

        firmwareVersionRepository.updateById(firmwareVersion);
        eventPublisher.publishEvent(new FirmwareChangedEvent(
                this,
                firmwareVersion.getProductId(),
                firmwareVersion.getId(),
                firmwareVersion.getVersion(),
                firmwareVersion.getInternalVersion(),
                ChangeType.UPDATED));

        log.info("固件版本更新成功: firmwareVersionId={}", firmwareVersion.getId());
        return firmwareVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFirmwareVersion(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "固件版本 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除固件版本: firmwareVersionId={}", id);
        }

        // 检查固件版本是否存在
        FirmwareVersion existingVersion = getById(id);

        boolean result = firmwareVersionRepository.deleteById(id);
        eventPublisher.publishEvent(new FirmwareChangedEvent(
                this,
                existingVersion.getProductId(),
                id,
                existingVersion.getVersion(),
                existingVersion.getInternalVersion(),
                ChangeType.DELETED));
        log.info("固件版本删除成功: firmwareVersionId={}, result={}", id, result);
        return result;
    }
}
