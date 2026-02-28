package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.FirmwareVersionAssembler;
import com.wewins.fota.adapter.api.admin.dto.firmware.AttachPackageReqDTO;
import com.wewins.fota.application.common.ReferenceNameResolver;
import com.wewins.fota.application.firmware.FirmwareVersionAppService;
import com.wewins.fota.application.firmware.dto.FirmwareVersionPageReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionRespDTO;
import com.wewins.fota.application.firmware.upload.FirmwareUploadAppService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import com.wewins.fota.storage.core.FileTransferService;
import com.wewins.fota.storage.naming.StorageObjectKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 固件版本管理控制器
 * <p>
 * 提供固件版本 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/firmware-versions")
@RequiredArgsConstructor
public class FirmwareVersionController {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String PACKAGE_STATUS_READY = "READY";
    private static final String PACKAGE_STATUS_NONE = "NONE";

    private final FirmwareVersionAppService firmwareVersionAppService;
    private final FirmwareVersionAssembler firmwareVersionAssembler;
    private final FirmwareUploadAppService firmwareUploadAppService;
    private final FileTransferService fileTransferService;
    private final StorageObjectKeyGenerator storageObjectKeyGenerator;
    private final ProductRepository productRepository;
    private final ReferenceNameResolver referenceNameResolver;

    /**
     * 分页查询固件版本列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<PageResponse<FirmwareVersionRespDTO>> listFirmwareVersions(@ModelAttribute FirmwareVersionPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new FirmwareVersionPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询固件版本: productId={}, version={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<FirmwareVersion> pageResult = firmwareVersionAppService.pageFirmwareVersions(reqDTO);
        List<FirmwareVersion> firmwareVersions = pageResult.getRecords();

        // 提取当前页中所有不同的产品 ID
        Set<Long> productIds = firmwareVersions.stream()
                .map(FirmwareVersion::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 使用 ReferenceNameResolver 批量查询产品名称
        Map<Long, String> productNameMap = referenceNameResolver.resolveProductNames(productIds);

        // 转换为 DTO，填充产品名称
        List<FirmwareVersionRespDTO> records = firmwareVersions.stream()
                .map(fv -> firmwareVersionAssembler.toFirmwareVersionResp(
                        fv,
                        productNameMap.get(fv.getProductId())
                ))
                .toList();

        PageResponse<FirmwareVersionRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    /**
     * 根据产品 ID 查询固件版本列表
     *
     * @param productId 产品 ID
     * @return 固件版本列表
     */
    @GetMapping("/by-product/{productId}")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<List<FirmwareVersionRespDTO>> listByProductId(
            @PathVariable Long productId,
            @RequestParam(value = "readyOnly", required = false, defaultValue = "false") boolean readyOnly) {
        if (productId == null || productId <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("查询产品固件版本列表: productId={}, readyOnly={}", productId, readyOnly);
        }

        try {
            List<FirmwareVersion> firmwareVersions = firmwareVersionAppService.listByProductId(productId);
            List<FirmwareVersionRespDTO> records = firmwareVersions.stream()
                    .filter(fv -> !readyOnly || PACKAGE_STATUS_READY.equalsIgnoreCase(fv.getPackageStatus()))
                    .map(firmwareVersionAssembler::toFirmwareVersionResp)
                    .toList();
            return ApiResponse.success(records);
        } catch (BizException e) {
            log.warn("查询产品固件版本列表失败: productId={}, errorCode={}, message={}",
                    productId, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * 获取固件版本详情
     *
     * @param id 版本 ID
     * @return 版本详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<FirmwareVersionRespDTO> getFirmwareVersion(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取固件版本详情: firmwareVersionId={}", id);
        }

        try {
            FirmwareVersion firmwareVersion = firmwareVersionAppService.getById(id);
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(firmwareVersion));
        } catch (BizException e) {
            log.warn("获取固件版本详情失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取固件版本详情参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 创建固件版本
     *
     * @param reqDTO 固件版本信息
     * @return 创建的固件版本
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:firmware:create')")
    public ApiResponse<FirmwareVersionRespDTO> createFirmwareVersion(@RequestBody FirmwareVersionReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建固件版本: productId={}, version={}",
                    reqDTO.getProductId(), reqDTO.getVersion());
        }

        try {
            // 处理上传会话ID（如果提供）
            ProcessedUploadSession processedUpload = processUploadSessionId(reqDTO);
            FirmwareVersionReqDTO processedReqDTO = processedUpload.requestDTO();

            FirmwareVersion firmwareVersion = firmwareVersionAssembler.toFirmwareVersionEntity(processedReqDTO);
            firmwareVersion.setId(null);

            // 设置包状态：有上传会话则为 READY，否则根据文件信息判断
            if (processedUpload.uploadSessionId() != null && !processedUpload.uploadSessionId().isBlank()) {
                firmwareVersion.setPackageStatus(PACKAGE_STATUS_READY);
                firmwareVersion.setPackageUploadedAt(LocalDateTime.now());
            } else {
                firmwareVersion.setPackageStatus(determinePackageStatus(processedReqDTO));
            }

            FirmwareVersion createdVersion = firmwareVersionAppService.createFirmwareVersion(firmwareVersion);
            log.info("固件版本创建成功: firmwareVersionId={}, productId={}, version={}",
                    createdVersion.getId(), createdVersion.getProductId(), createdVersion.getVersion());

            // 业务成功后清理上传会话
            cleanupUploadSession(processedUpload.uploadSessionId());

            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(createdVersion));
        } catch (BizException e) {
            log.warn("创建固件版本失败: productId={}, version={}, errorCode={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建固件版本参数错误: productId={}, version={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 更新固件版本
     *
     * @param id 固件版本 ID
     * @param reqDTO 固件版本信息
     * @return 更新后的固件版本
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:update')")
    public ApiResponse<FirmwareVersionRespDTO> updateFirmwareVersion(@PathVariable Long id, @RequestBody FirmwareVersionReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新固件版本: firmwareVersionId={}", id);
        }

        try {
            // 查询现有版本（用于保留包状态）
            FirmwareVersion existingVersion = firmwareVersionAppService.getById(id);

            // 处理上传会话ID（如果提供）
            ProcessedUploadSession processedUpload = processUploadSessionId(reqDTO);
            FirmwareVersionReqDTO processedReqDTO = processedUpload.requestDTO();

            // 保存旧的 objectKey（用于后续清理，仅在补传包场景需要）
            String oldObjectKey = null;
            String newObjectKey = null;

            FirmwareVersion firmwareVersion = firmwareVersionAssembler.toFirmwareVersionEntity(processedReqDTO);
            firmwareVersion.setId(id);

            // 处理包状态：如果有uploadSessionId说明是补传包，设置READY；否则保留原有状态
            if (processedUpload.uploadSessionId() != null && !processedUpload.uploadSessionId().isBlank()) {
                // 补传包场景，processUploadSessionId已填充文件字段
                oldObjectKey = existingVersion.getFileUrl();
                newObjectKey = processedReqDTO.getFileUrl();
                firmwareVersion.setPackageStatus(PACKAGE_STATUS_READY);
                firmwareVersion.setPackageUploadedAt(LocalDateTime.now());
            } else {
                // 普通编辑场景，保留原有的包状态和文件字段
                firmwareVersion.setPackageStatus(existingVersion.getPackageStatus());
                firmwareVersion.setFileUrl(existingVersion.getFileUrl());
                firmwareVersion.setFileSize(existingVersion.getFileSize());
                firmwareVersion.setMd5(existingVersion.getMd5());
                firmwareVersion.setSha256(existingVersion.getSha256());
                firmwareVersion.setPackageUploadedAt(existingVersion.getPackageUploadedAt());
            }

            FirmwareVersion updatedVersion = firmwareVersionAppService.updateFirmwareVersion(firmwareVersion);
            log.info("固件版本更新成功: firmwareVersionId={}", updatedVersion.getId());

            // 业务成功后清理上传会话
            cleanupUploadSession(processedUpload.uploadSessionId());

            // 如果是补传包场景，清理被替换的旧包文件
            if (oldObjectKey != null && newObjectKey != null) {
                cleanupReplacedObject(oldObjectKey, newObjectKey, id);
            }

            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(updatedVersion));
        } catch (BizException e) {
            log.warn("更新固件版本失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新固件版本参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 获取固件版本下载地址。
     * <p>
     * 根据数据库中存储的 objectKey 生成带签名的下载 URL。
     * 签名 URL 默认有效期为 1 小时。
     * </p>
     *
     * @param id 版本 ID
     * @return 下载地址
     */
    @GetMapping("/{id}/download-url")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<String> getDownloadUrl(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        try {
            FirmwareVersion firmwareVersion = firmwareVersionAppService.getById(id);

            // 检查是否有固件包
            if (!PACKAGE_STATUS_READY.equalsIgnoreCase(firmwareVersion.getPackageStatus())) {
                return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "该固件版本没有可下载的包");
            }

            String objectKey = firmwareVersion.getFileUrl();
            if (objectKey == null || objectKey.isBlank()) {
                return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "固件包路径缺失");
            }

            // 生成带签名的下载 URL（有效期 1 小时）
            String downloadUrl = fileTransferService.getDownloadUrl(objectKey, java.time.Duration.ofHours(1));

            if (log.isDebugEnabled()) {
                log.debug("生成固件下载地址: firmwareVersionId={}, objectKey={}", id, objectKey);
            }

            return ApiResponse.success(downloadUrl);
        } catch (BizException e) {
            log.warn("获取下载地址失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取下载地址参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 删除固件版本
     *
     * @param id 版本 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:delete')")
    public ApiResponse<Void> deleteFirmwareVersion(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除固件版本: firmwareVersionId={}", id);
        }

        try {
            firmwareVersionAppService.deleteFirmwareVersion(id);
            log.info("固件版本删除成功: firmwareVersionId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除固件版本失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除固件版本参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 处理上传会话ID，如果提供则消费会话并填充文件信息。
     * <p>
     * 先读后删策略：这里只读取会话并转存文件，不删除会话。
     * 会话删除在业务成功后由 cleanupUploadSession 执行。
     * </p>
     *
     * @param reqDTO 原始请求DTO
     * @return 处理后的请求DTO与会话ID
     */
    private ProcessedUploadSession processUploadSessionId(FirmwareVersionReqDTO reqDTO) {
        if (reqDTO.getUploadSessionId() == null || reqDTO.getUploadSessionId().isBlank()) {
            return new ProcessedUploadSession(reqDTO, null);
        }

        String sessionId = reqDTO.getUploadSessionId();
        FirmwareUploadSession session = loadAndValidateUploadSession(sessionId, reqDTO.getProductId());
        String objectKey = ensureObjectKeyReady(sessionId, reqDTO.getProductId(), session);

        // 使用会话中的文件信息填充DTO
        FirmwareVersionReqDTO processed = new FirmwareVersionReqDTO();
        processed.setProductId(reqDTO.getProductId());
        processed.setVersion(reqDTO.getVersion());
        processed.setFileUrl(objectKey);
        processed.setFileName(session.getFileName());  // 保存原始文件名
        processed.setFileSize(session.getFileSize());
        processed.setMd5(session.getMd5());
        processed.setSha256(session.getSha256());
        processed.setTags(reqDTO.getTags());
        processed.setMeta(reqDTO.getMeta());

        return new ProcessedUploadSession(processed, sessionId);
    }

    /**
     * 根据文件信息判断包状态。
     *
     * @param reqDTO 请求DTO
     * @return 包状态（READY/NONE）
     */
    private String determinePackageStatus(FirmwareVersionReqDTO reqDTO) {
        boolean hasPackageInfo = reqDTO.getFileUrl() != null && !reqDTO.getFileUrl().isBlank()
                && reqDTO.getFileSize() != null && reqDTO.getFileSize() > 0
                && reqDTO.getMd5() != null && !reqDTO.getMd5().isBlank()
                && reqDTO.getSha256() != null && !reqDTO.getSha256().isBlank();

        return hasPackageInfo ? PACKAGE_STATUS_READY : PACKAGE_STATUS_NONE;
    }

    /**
     * 为已有版本补传固件包。
     *
     * @param id      固件版本ID
     * @param reqDTO  请求参数（包含uploadSessionId）
     * @return 更新后的固件版本
     */
    @PostMapping("/{id}/attach-package")
    @PreAuthorize("@rbac.has('fota:firmware:update')")
    public ApiResponse<FirmwareVersionRespDTO> attachPackage(
            @PathVariable Long id,
            @RequestBody @Valid AttachPackageReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        try {
            // 查询现有版本
            FirmwareVersion existingVersion = firmwareVersionAppService.getById(id);

            String sessionId = reqDTO.uploadSessionId();
            FirmwareUploadSession session = loadAndValidateUploadSession(sessionId, existingVersion.getProductId());
            String objectKey = ensureObjectKeyReady(sessionId, existingVersion.getProductId(), session);

            // 保存旧的 objectKey（用于后续清理）
            String oldObjectKey = existingVersion.getFileUrl();

            // 更新版本（仅更新包相关字段）
            FirmwareVersionReqDTO updateDTO = new FirmwareVersionReqDTO();
            updateDTO.setProductId(existingVersion.getProductId());
            updateDTO.setVersion(existingVersion.getVersion());
            updateDTO.setFileUrl(objectKey);
            updateDTO.setFileName(session.getFileName());  // 保存原始文件名
            updateDTO.setFileSize(session.getFileSize());
            updateDTO.setMd5(session.getMd5());
            updateDTO.setSha256(session.getSha256());
            updateDTO.setTags(existingVersion.getTags() != null ? existingVersion.getTags().toString() : null);
            updateDTO.setMeta(existingVersion.getMeta() != null ? existingVersion.getMeta().toString() : null);

            FirmwareVersion firmwareVersion = firmwareVersionAssembler.toFirmwareVersionEntity(updateDTO);
            firmwareVersion.setId(id);
            firmwareVersion.setPackageStatus(PACKAGE_STATUS_READY);
            firmwareVersion.setPackageUploadedAt(LocalDateTime.now());

            FirmwareVersion updatedVersion = firmwareVersionAppService.updateFirmwareVersion(firmwareVersion);

            // 业务成功后清理上传会话
            cleanupUploadSession(sessionId);

            // 清理被替换的旧包文件（如果存在）
            cleanupReplacedObject(oldObjectKey, objectKey, id);

            log.info("固件版本补传包成功: firmwareVersionId={}", id);
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(updatedVersion));
        } catch (BizException e) {
            log.warn("补传固件包失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("补传固件包参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 加载并校验上传会话。
     *
     * @param sessionId         会话 ID
     * @param expectedProductId 期望的产品 ID
     * @return 上传会话
     */
    private FirmwareUploadSession loadAndValidateUploadSession(String sessionId, Long expectedProductId) {
        FirmwareUploadSession session = firmwareUploadAppService.getUploadSession(sessionId);
        if (session.getProductId() != null && !session.getProductId().equals(expectedProductId)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "上传会话与产品不匹配");
        }
        return session;
    }

    /**
     * 确保会话中的文件已可在对象存储访问。
     * <p>
     * 先读后删策略：这里只更新会话，不删除会话，删除动作在业务成功后执行。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param productId 产品 ID
     * @param session   上传会话
     * @return 对象存储 Key
     */
    private String ensureObjectKeyReady(String sessionId, Long productId, FirmwareUploadSession session) {
        // 如果已经有 objectKey，直接返回（已转存过）
        if (session.getObjectKey() != null && !session.getObjectKey().isBlank()) {
            return session.getObjectKey();
        }

        // 检查是否有临时路径
        if (session.getTempPath() == null || session.getTempPath().isBlank()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(),
                    "上传会话文件信息不完整：缺少临时路径和对象Key");
        }

        Path tempPath = Path.of(session.getTempPath());
        try {
            // 生成对象存储 Key 并转存
            String objectKey = storageObjectKeyGenerator.generateFirmwarePackageKey(productId, session.getFileName());
            String s3Uri = fileTransferService.transferToStorage(
                    tempPath,
                    objectKey,
                    session.getMime() != null ? session.getMime() : DEFAULT_CONTENT_TYPE,
                    true  // deleteAfterTransfer
            );

            if (log.isDebugEnabled()) {
                log.debug("固件文件已转存到对象存储: sessionId={}, objectKey={}, s3Uri={}",
                        sessionId, objectKey, s3Uri);
            }

            // 回写会话，保证后续失败重试可直接复用 objectKey
            session.setObjectKey(objectKey);
            session.setTempPath(null);
            session.setStatus(FirmwareUploadSession.UploadStatus.READY);
            session.setUpdatedAt(LocalDateTime.now());
            firmwareUploadAppService.saveUploadSession(session);

            return objectKey;
        } catch (Exception e) {
            log.error("转存固件文件到对象存储失败: sessionId={}", sessionId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "转存固件文件失败", e);
        }
    }

    /**
     * 清理上传会话（在业务成功后调用）。
     *
     * @param sessionId 会话 ID
     */
    private void cleanupUploadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            boolean deleted = firmwareUploadAppService.deleteUploadSession(sessionId);
            if (!deleted) {
                log.warn("上传会话删除失败或已不存在: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            // 主流程已成功，不因会话清理失败回滚
            log.warn("清理上传会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 清理被替换的旧包文件。
     * <p>
     * 采用"先上传后删除"策略：
     * 1. 先将新文件上传到对象存储
     * 2. 更新数据库记录
     * 3. 数据库更新成功后，删除旧的对象存储文件
     * </p>
     * <p>
     * 如果删除失败，仅记录日志，不影响主流程（可能导致孤儿文件，但可通过后续清理任务处理）
     * </p>
     *
     * @param oldObjectKey   旧的对象存储键（可能为 null）
     * @param newObjectKey   新的对象存储键
     * @param firmwareVersionId 固件版本 ID（用于日志）
     */
    private void cleanupReplacedObject(String oldObjectKey, String newObjectKey, Long firmwareVersionId) {
        if (oldObjectKey == null || oldObjectKey.isBlank()) {
            // 无旧包，无需清理
            return;
        }
        if (oldObjectKey.equals(newObjectKey)) {
            // 新旧包相同，无需清理（理论上不应发生）
            log.warn("新旧对象存储键相同，跳过清理: firmwareVersionId={}, objectKey={}",
                    firmwareVersionId, oldObjectKey);
            return;
        }
        try {
            fileTransferService.deleteStorageObject(oldObjectKey);
            log.info("清理旧固件包成功: firmwareVersionId={}, oldObjectKey={}", firmwareVersionId, oldObjectKey);
        } catch (RuntimeException e) {
            // 只捕获预期的运行时异常，让 Error 级别异常向上传播
            // 删除失败不影响主流程，记录日志供后续清理
            log.error("清理旧固件包失败（将产生孤儿文件）: firmwareVersionId={}, oldObjectKey={}",
                    firmwareVersionId, oldObjectKey, e);
        }
    }

    /**
     * 上传会话处理结果。
     *
     * @param requestDTO      已用会话信息填充后的请求 DTO
     * @param uploadSessionId 关联会话 ID（成功后用于删除）
     */
    private record ProcessedUploadSession(
            FirmwareVersionReqDTO requestDTO,
            String uploadSessionId
    ) {}

}
