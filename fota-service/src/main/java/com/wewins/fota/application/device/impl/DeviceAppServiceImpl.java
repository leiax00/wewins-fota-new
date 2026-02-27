package com.wewins.fota.application.device.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.application.device.dto.BatchOperationReqDTO;
import com.wewins.fota.application.device.dto.BatchOperationResultDTO;
import com.wewins.fota.application.device.dto.DeviceImportRespDTO;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.parser.DeviceImportFileParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private final DeviceImportBatchRepository deviceImportBatchRepository;
    private final DeviceImportFileParserFactory parserFactory;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 1000;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceImportRespDTO importDevices(MultipartFile file, Long productId, String batchName) throws IOException {
        // 参数校验
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "导入文件不能为空");
        }

        if (productId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品ID不能为空");
        }

        // 验证产品存在
        productRepository.findById(productId)
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));

        // 生成批次名称
        if (batchName == null || batchName.isBlank()) {
            batchName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }

        log.info("开始导入设备: productId={}, batchName={}, filename={}",
                productId, batchName, file.getOriginalFilename());

        // 创建批次记录
        DeviceImportBatch batch = DeviceImportBatch.builder()
                .batchName(batchName)
                .status("IMPORTING")
                .sourceFile(file.getOriginalFilename())
                .totalCount(0)
                .successCount(0)
                .failedCount(0)
                .startedAt(LocalDateTime.now())
                .build();
        deviceImportBatchRepository.create(batch);
        deviceImportBatchRepository.updateById(batch);

        try {
            // 解析文件获取IMEI列表
            var parser = parserFactory.getParser(file.getOriginalFilename());
            List<String> imeis = parser.parse(file.getInputStream());

            if (imeis.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件中未找到有效的IMEI");
            }

            batch.setTotalCount(imeis.size());
            deviceImportBatchRepository.updateById(batch);

            log.info("文件解析完成，共{}个IMEI", imeis.size());

            // 批量创建设备
            int successCount = 0;
            int failedCount = 0;
            int updatedCount = 0;
            List<Device> devicesToCreate = new ArrayList<>(BATCH_SIZE);
            List<Device> devicesToUpdate = new ArrayList<>(BATCH_SIZE);

            for (String imei : imeis) {
                try {
                    // 验证IMEI格式
                    String normalizedImei = normalizeImei(imei);

                    // 检查IMEI是否已存在
                    var existingDevice = deviceRepository.findByImei(normalizedImei);
                    if (existingDevice.isPresent()) {
                        // 设备已存在，更新批次ID
                        Device device = existingDevice.get();
                        // 只有当批次ID不同时才更新
                        if (!batch.getId().equals(device.getImportBatchId())) {
                            device.setImportBatchId(batch.getId());
                            devicesToUpdate.add(device);

                            // 批量更新
                            if (devicesToUpdate.size() >= BATCH_SIZE) {
                                for (Device d : devicesToUpdate) {
                                    deviceRepository.updateById(d);
                                }
                                updatedCount += devicesToUpdate.size();
                                log.info("已更新{}个设备的批次ID", updatedCount);
                                devicesToUpdate.clear();
                            }
                        }
                        successCount++;
                        log.debug("IMEI已存在，已更新批次ID: {}", normalizedImei);
                    } else {
                        // 创建新设备
                        Device device = Device.builder()
                                .imei(normalizedImei)
                                .productId(productId)
                                .status("OFFLINE")
                                .importBatchId(batch.getId())
                                .build();

                        devicesToCreate.add(device);

                        // 批量插入
                        if (devicesToCreate.size() >= BATCH_SIZE) {
                            deviceRepository.batchCreate(devicesToCreate);
                            successCount += devicesToCreate.size();
                            log.info("已导入{}个设备", successCount);
                            devicesToCreate.clear();
                        }
                    }

                } catch (Exception e) {
                    log.warn("导入IMEI失败: {}, 错误: {}", imei, e.getMessage());
                    failedCount++;
                }
            }

            // 插入剩余设备
            if (!devicesToCreate.isEmpty()) {
                deviceRepository.batchCreate(devicesToCreate);
                successCount += devicesToCreate.size();
            }

            // 更新剩余设备
            if (!devicesToUpdate.isEmpty()) {
                for (Device d : devicesToUpdate) {
                    deviceRepository.updateById(d);
                }
                updatedCount += devicesToUpdate.size();
            }

            // 更新批次状态
            batch.setSuccessCount(successCount);
            batch.setFailedCount(failedCount);
            batch.setFinishedAt(LocalDateTime.now());

            if (failedCount == 0) {
                batch.setStatus("SUCCESS");
            } else if (successCount == 0) {
                batch.setStatus("FAILED");
            } else {
                batch.setStatus("PARTIAL");
            }
            deviceImportBatchRepository.updateById(batch);

            log.info("设备导入完成: batchId={}, total={}, success={} (新建={}, 更新={}), failed={}",
                    batch.getId(), batch.getTotalCount(), successCount, successCount - updatedCount, updatedCount, failedCount);

            return DeviceImportRespDTO.builder()
                    .batchId(batch.getId())
                    .batchName(batch.getBatchName())
                    .status(batch.getStatus())
                    .totalCount(batch.getTotalCount())
                    .successCount(batch.getSuccessCount())
                    .failedCount(batch.getFailedCount())
                    .build();

        } catch (IOException e) {
            // 更新批次为失败状态
            batch.setStatus("FAILED");
            batch.setErrorMessage(e.getMessage());
            batch.setFinishedAt(LocalDateTime.now());
            deviceImportBatchRepository.updateById(batch);

            log.error("设备导入失败: batchId={}, error={}", batch.getId(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // 更新批次为失败状态
            batch.setStatus("FAILED");
            batch.setErrorMessage(e.getMessage());
            batch.setFinishedAt(LocalDateTime.now());
            deviceImportBatchRepository.updateById(batch);

            log.error("设备导入失败: batchId={}, error={}", batch.getId(), e.getMessage(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "设备导入失败: " + e.getMessage());
        }
    }

    @Override
    public int estimateBatchOperation(BatchOperationReqDTO reqDTO) {
        if (reqDTO == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "批量操作请求参数不能为空");
        }

        // 验证请求参数
        reqDTO.validate();

        // 根据操作类型查询目标设备
        List<Device> devices = findDevicesForBatchOperation(reqDTO);
        return devices.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResultDTO executeBatchOperation(BatchOperationReqDTO reqDTO) {
        if (reqDTO == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "批量操作请求参数不能为空");
        }

        // 验证请求参数
        reqDTO.validate();

        log.info("开始执行批量操作: operationType={}", reqDTO.getOperationType());

        // 查询目标设备
        List<Device> devices = findDevicesForBatchOperation(reqDTO);
        int totalCount = devices.size();

        if (totalCount == 0) {
            log.warn("批量操作未找到任何设备");
            return BatchOperationResultDTO.builder()
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .errors(List.of("未找到任何符合条件的设备"))
                    .build();
        }

        log.info("批量操作将影响 {} 个设备", totalCount);

        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        // 根据操作类型执行批量操作
        switch (reqDTO.getOperationType()) {
            case DELETE_BY_BATCH -> {
                List<Long> deviceIds = devices.stream().map(Device::getId).toList();
                int deleted = deviceRepository.batchSoftDelete(deviceIds);
                successCount = deleted;
                failedCount = totalCount - deleted;
                log.info("批量删除完成: 总数={}, 成功={}, 失败={}", totalCount, successCount, failedCount);
            }
            case UPDATE_TAG_BY_BATCH, UPDATE_TAG_BY_IMEI, UPDATE_TAG_BY_QUERY -> {
                // 验证JSON格式
                try {
                    objectMapper.readTree(reqDTO.getTags());
                } catch (Exception e) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "标签JSON格式不正确: " + e.getMessage());
                }

                List<Long> deviceIds = devices.stream().map(Device::getId).toList();
                deviceRepository.batchUpdateTags(deviceIds, reqDTO.getTags());
                successCount = deviceIds.size();
                log.info("批量更新标签完成: 总数={}, 成功={}", totalCount, successCount);
            }
            case UPDATE_BATCH_BY_IMEI, UPDATE_BATCH_BY_QUERY -> {
                // 验证新批次存在
                deviceImportBatchRepository.findById(reqDTO.getNewBatchId())
                        .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST.getCode(), "目标批次不存在"));

                List<Long> deviceIds = devices.stream().map(Device::getId).toList();
                deviceRepository.batchUpdateImportBatchId(deviceIds, reqDTO.getNewBatchId());
                successCount = deviceIds.size();
                log.info("批量更新批次完成: 总数={}, 成功={}", totalCount, successCount);
            }
            default -> {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的操作类型: " + reqDTO.getOperationType());
            }
        }

        // 构建返回结果
        return BatchOperationResultDTO.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .errors(errors.isEmpty() ? null : errors)
                .build();
    }

    /**
     * 根据批量操作请求查找目标设备
     */
    private List<Device> findDevicesForBatchOperation(BatchOperationReqDTO reqDTO) {
        return switch (reqDTO.getOperationType()) {
            case DELETE_BY_BATCH, UPDATE_TAG_BY_BATCH ->
                deviceRepository.findAllByImportBatchId(reqDTO.getBatchId());
            case UPDATE_TAG_BY_IMEI, UPDATE_BATCH_BY_IMEI ->
                deviceRepository.findByImeis(reqDTO.getImeis());
        };
    }
}
