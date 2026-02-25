package com.wewins.fota.application.device.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 设备应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAppServiceImpl implements DeviceAppService {

    private static final Pattern IMEI_PATTERN = Pattern.compile("^\\d{15}$");
    private static final Set<String> ALLOWED_STATUS = Set.of("ONLINE", "OFFLINE", "LOST");

    private final DeviceRepository deviceRepository;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;

    @Override
    public Page<Device> pageDevices(DevicePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DevicePageReqDTO();
        }
        reqDTO.validate();

        Page<Device> page = new Page<>(reqDTO.getPage(), reqDTO.getSize());

        if (log.isDebugEnabled()) {
            log.debug("分页查询设备: productId={}, imei={}, status={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getImei(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        return deviceRepository.pageDevices(page, reqDTO.getProductId(), reqDTO.getImei(), reqDTO.getStatus());
    }

    @Override
    public Device getById(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return deviceRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_NOT_FOUND));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device createDevice(Device device) {
        if (device == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "设备信息不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("创建设备: imei={}, productId={}, currentVersionId={}, status={}",
                    device.getImei(), device.getProductId(), device.getCurrentVersionId(), device.getStatus());
        }

        normalizeAndValidate(device, true);
        deviceRepository.create(device);
        log.info("设备创建成功: deviceId={}, imei={}", device.getId(), device.getImei());
        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device updateDevice(Device device) {
        if (device == null || device.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "设备 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新设备: deviceId={}, imei={}, productId={}, currentVersionId={}, status={}",
                    device.getId(), device.getImei(), device.getProductId(), device.getCurrentVersionId(), device.getStatus());
        }

        getById(device.getId());
        normalizeAndValidate(device, false);
        deviceRepository.updateById(device);
        log.info("设备更新成功: deviceId={}", device.getId());
        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDevice(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "设备 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除设备: deviceId={}", id);
        }

        getById(id);
        boolean result = deviceRepository.softDeleteById(id);
        log.info("设备删除成功: deviceId={}, result={}", id, result);
        return result;
    }

    private void normalizeAndValidate(Device device, boolean creating) {
        String normalizedImei = normalizeImei(device.getImei());
        String normalizedStatus = normalizeStatus(device.getStatus());

        if (device.getProductId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        productRepository.findById(device.getProductId())
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));

        Long currentVersionId = device.getCurrentVersionId();
        if (currentVersionId != null) {
            FirmwareVersion firmwareVersion = firmwareVersionRepository.findById(currentVersionId)
                    .orElseThrow(() -> new BizException(ErrorCode.FIRMWARE_VERSION_NOT_FOUND));
            if (!device.getProductId().equals(firmwareVersion.getProductId())) {
                throw new BizException(ErrorCode.DEVICE_FIRMWARE_PRODUCT_MISMATCH);
            }
        }

        Long excludeId = creating ? null : device.getId();
        long duplicateCount = deviceRepository.countByImeiExcludingId(normalizedImei, excludeId);
        if (duplicateCount > 0) {
            throw new BizException(ErrorCode.DEVICE_IMEI_EXISTS);
        }

        device.setImei(normalizedImei);
        device.setStatus(normalizedStatus);
    }

    private String normalizeImei(String imei) {
        if (imei == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "IMEI 不能为空");
        }

        String normalized = imei.trim();
        if (!IMEI_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.DEVICE_IMEI_INVALID);
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalizedStatus;
        if (status == null || status.isBlank()) {
            normalizedStatus = "OFFLINE";
        } else {
            normalizedStatus = status.trim().toUpperCase();
        }

        if (!ALLOWED_STATUS.contains(normalizedStatus)) {
            throw new BizException(ErrorCode.DEVICE_STATUS_INVALID);
        }
        return normalizedStatus;
    }
}
