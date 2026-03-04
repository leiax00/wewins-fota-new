package com.wewins.fota.application.device.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.device.DeviceAppService;
import com.wewins.fota.application.device.dto.BatchOperationReqDTO;
import com.wewins.fota.application.device.dto.BatchOperationResultDTO;
import com.wewins.fota.application.device.dto.DeviceImportEstimateRespDTO;
import com.wewins.fota.application.device.dto.DeviceImportExecuteReqDTO;
import com.wewins.fota.application.device.dto.DeviceImportRespDTO;
import com.wewins.fota.application.device.dto.DevicePageReqDTO;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.dto.DeviceImportSession;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.cache.event.ChangeType;
import com.wewins.fota.infra.cache.event.DeviceBatchChangedEvent;
import com.wewins.fota.infra.cache.event.DeviceChangedEvent;
import com.wewins.fota.infra.parser.DeviceImportFileParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(new DeviceChangedEvent(this, device.getImei(), ChangeType.CREATED));
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
        eventPublisher.publishEvent(new DeviceChangedEvent(this, device.getImei(), ChangeType.UPDATED));
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

        Device existingDevice = getById(id);
        boolean result = deviceRepository.softDeleteById(id);
        eventPublisher.publishEvent(new DeviceChangedEvent(this, existingDevice.getImei(), ChangeType.DELETED));
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
    public DeviceImportEstimateRespDTO estimateImportDevices(MultipartFile file) throws IOException {
        DeviceImportSession session = parseImeiFile(file);
        String sessionId = session.getSessionId();

        // 保存到 Redis
        String redisKey = String.format(RedisKeyConstants.DEVICE_IMPORT_SESSION_KEY_TEMPLATE, sessionId);
        redisTemplate.opsForValue().set(
                redisKey,
                session,
                RedisKeyConstants.DEVICE_IMPORT_SESSION_TTL_SECONDS,
                TimeUnit.SECONDS
        );


        log.info("设备导入预估完成: sessionId={}, total={}, valid={}, invalid={}",
                sessionId, session.getTotalCount(), session.getValidCount(), session.getInvalidCount());

        return DeviceImportEstimateRespDTO.builder()
                .sessionId(sessionId)
                .totalCount(session.getTotalCount())
                .validCount(session.getValidCount())
                .invalidCount(session.getInvalidCount())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceImportRespDTO executeImportDevices(DeviceImportExecuteReqDTO reqDTO) {
        // 参数校验
        if (reqDTO == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请求参数不能为空");
        }

        if (reqDTO.getProductId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品ID不能为空");
        }

        // 验证产品存在
        productRepository.findById(reqDTO.getProductId())
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));

        // 生成批次名称
        String batchName = reqDTO.getBatchName();
        if (batchName == null || batchName.isBlank()) {
            batchName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }

        // 获取 IMEI 列表（从 sessionId 或请求参数）
        List<String> imeis;
        boolean fromFile = false;

        if (reqDTO.getSessionId() != null && !reqDTO.getSessionId().isBlank()) {
            // 从 Redis 会话中获取
            String redisKey = String.format(RedisKeyConstants.DEVICE_IMPORT_SESSION_KEY_TEMPLATE, reqDTO.getSessionId());
            DeviceImportSession session = (DeviceImportSession) redisTemplate.opsForValue().get(redisKey);

            if (session == null) {
                throw new BizException(ErrorCode.NOT_FOUND.getCode(), "导入会话不存在或已过期");
            }

            imeis = session.getImeis();
            fromFile = session.isFromFile();
            log.info("从会话获取IMEI列表: sessionId={}, count={}", reqDTO.getSessionId(), imeis.size());
        } else if (reqDTO.getImeis() != null && !reqDTO.getImeis().isEmpty()) {
            // 直接使用请求中的 IMEI 列表
            imeis = reqDTO.getImeis();
            log.info("使用请求中的IMEI列表: count={}", imeis.size());
        } else {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "必须提供 sessionId 或 imeis");
        }

        log.info("开始执行设备导入: productId={}, batchName={}, imeiCount={}, fromFile={}",
                reqDTO.getProductId(), batchName, imeis.size(), fromFile);

        // 检查是否存在相同批次名称和产品ID的批次
        DeviceImportBatch batch = deviceImportBatchRepository
                .findByBatchNameAndProductId(batchName, reqDTO.getProductId())
                .orElse(null);

        if (batch != null) {
            // 批次已存在，累加统计
            log.info("批次已存在，累加统计: batchId={}, batchName={}", batch.getId(), batch.getBatchName());
            batch.setStatus("IMPORTING");
            batch.setStartedAt(LocalDateTime.now());
            // 注意：totalCount 会累加，表示该批次总共处理了多少设备
            batch.setTotalCount(batch.getTotalCount() + imeis.size());
        } else {
            // 创建新批次
            batch = DeviceImportBatch.builder()
                    .batchName(batchName)
                    .productId(reqDTO.getProductId())
                    .status("IMPORTING")
                    .sourceFile(reqDTO.getSourceFile() != null ? reqDTO.getSourceFile() : (fromFile ? "文件导入" : "文本输入"))
                    .totalCount(imeis.size())
                    .successCount(0)
                    .failedCount(0)
                    .startedAt(LocalDateTime.now())
                    .build();
            deviceImportBatchRepository.create(batch);
        }

        try {
            // 批量创建设备
            int successCount = 0;
            int failedCount = 0;
            int updatedCount = 0;
            List<Device> devicesToCreate = new ArrayList<>(BATCH_SIZE);
            List<Device> devicesToUpdate = new ArrayList<>(BATCH_SIZE);

            for (String imei : imeis) {
                try {
                    // 检查IMEI是否已存在
                    var existingDevice = deviceRepository.findByImei(imei);
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
                        } else {
                            batch.setTotalCount(batch.getTotalCount() - 1);
                        }
                        successCount++;
                        log.debug("IMEI已存在，已更新批次ID: {}", imei);
                    } else {
                        // 创建新设备
                        Device device = Device.builder()
                                .imei(imei)
                                .productId(reqDTO.getProductId())
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
            // 注意：如果批次已存在，这里需要累加之前的统计
            int previousSuccessCount = batch.getSuccessCount() != null ? batch.getSuccessCount() : 0;
            int previousFailedCount = batch.getFailedCount() != null ? batch.getFailedCount() : 0;

            batch.setSuccessCount(previousSuccessCount + successCount);
            batch.setFailedCount(previousFailedCount + failedCount);
            batch.setFinishedAt(LocalDateTime.now());

            if (failedCount == 0 && previousFailedCount == 0) {
                batch.setStatus("SUCCESS");
            } else if (successCount == 0 && previousSuccessCount == 0) {
                batch.setStatus("FAILED");
            } else {
                batch.setStatus("PARTIAL");
            }
            deviceImportBatchRepository.updateById(batch);

            // 消费会话（如果是文件导入）
            if (reqDTO.getSessionId() != null && !reqDTO.getSessionId().isBlank()) {
                String redisKey = String.format(RedisKeyConstants.DEVICE_IMPORT_SESSION_KEY_TEMPLATE, reqDTO.getSessionId());
                redisTemplate.delete(redisKey);
                log.info("设备导入会话已消费: sessionId={}", reqDTO.getSessionId());
            }

            log.info("设备导入完成: batchId={}, total={}, success={} (新建={}, 更新={}), failed={}",
                    batch.getId(), batch.getTotalCount(), batch.getSuccessCount(), successCount - updatedCount, updatedCount, batch.getFailedCount());

            if (!imeis.isEmpty()) {
                eventPublisher.publishEvent(new DeviceBatchChangedEvent(this, imeis, ChangeType.BATCH_IMPORTED));
            }

            return DeviceImportRespDTO.builder()
                    .batchId(batch.getId())
                    .batchName(batch.getBatchName())
                    .status(batch.getStatus())
                    .totalCount(batch.getTotalCount())
                    .successCount(batch.getSuccessCount())
                    .failedCount(batch.getFailedCount())
                    .build();

        } catch (BizException e) {
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
    public DeviceImportSession parseImeiFile(MultipartFile file) throws IOException {
        // 参数校验
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "导入文件不能为空");
        }

        log.info("开始预估设备导入: filename={}", file.getOriginalFilename());

        // 解析文件获取 IMEI 列表
        var parser = parserFactory.getParser(file.getOriginalFilename());
        List<String> rawImeis = parser.parse(file.getInputStream());

        if (rawImeis.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件中未找到有效的IMEI");
        }

        log.info("文件解析完成，共{}个原始IMEI", rawImeis.size());

        // 验证 IMEI 格式并统计
        List<String> validImeis = new ArrayList<>();
        int invalidCount = 0;

        for (String imei : rawImeis) {
            try {
                String normalizedImei = normalizeImei(imei);
                validImeis.add(normalizedImei);
            } catch (Exception e) {
                log.debug("IMEI 格式无效: {}", imei);
                invalidCount++;
            }
        }

        // 生成会话 ID
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();

        // 创建会话对象
        return DeviceImportSession.builder()
                .sessionId(sessionId)
                .fileName(file.getOriginalFilename())
                .imeis(validImeis)
                .totalCount(rawImeis.size())
                .validCount(validImeis.size())
                .invalidCount(invalidCount)
                .createdAt(now)
                .fromFile(true)
                .build();
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
            case UPDATE_TAG_BY_BATCH, UPDATE_TAG_BY_IMEI -> {
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
            case UPDATE_BATCH_BY_IMEI -> {
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
