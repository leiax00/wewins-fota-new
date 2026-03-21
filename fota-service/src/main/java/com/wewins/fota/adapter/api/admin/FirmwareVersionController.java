package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.FirmwareVersionAssembler;
import com.wewins.fota.application.firmware.FirmwarePublishService;
import com.wewins.fota.application.firmware.FirmwareVersionAppService;
import com.wewins.fota.application.firmware.FirmwareWarmMessage;
import com.wewins.fota.application.firmware.dto.FirmwareVersionPageReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionRespDTO;
import com.wewins.fota.application.product.query.ProductNameQueryService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.storage.core.FileTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final String PACKAGE_STATUS_READY = "READY";
    private final FirmwareVersionAppService firmwareVersionAppService;
    private final FirmwareVersionAssembler firmwareVersionAssembler;
    private final FirmwarePublishService firmwarePublishService;
    private final FileTransferService fileTransferService;
    private final ProductNameQueryService productNameQueryService;

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
        Map<Long, String> productNameMap = productNameQueryService.resolveProductNames(productIds);

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
                return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), FirmwareWarmMessage.PATH_MISSING.message());
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
     * 直接创建无包固件版本。
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:firmware:create')")
    public ApiResponse<FirmwareVersionRespDTO> createFirmwareVersion(@RequestBody @Valid FirmwareVersionReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        try {
            FirmwareVersion draft = firmwareVersionAssembler.toFirmwareVersionEntity(reqDTO);
            FirmwareVersion created = firmwarePublishService.createDirect(draft);
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(created));
        } catch (BizException e) {
            log.warn("直接创建固件版本失败: productId={}, version={}, errorCode={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("直接创建固件版本参数错误: productId={}, version={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 直接更新固件版本元数据或切换为无包版本。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:update')")
    public ApiResponse<FirmwareVersionRespDTO> updateFirmwareVersion(
            @PathVariable Long id,
            @RequestBody @Valid FirmwareVersionReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        try {
            FirmwareVersion draft = firmwareVersionAssembler.toFirmwareVersionEntity(reqDTO);
            FirmwareVersion updated = firmwarePublishService.updateDirect(id, draft);
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(updated));
        } catch (BizException e) {
            log.warn("直接更新固件版本失败: versionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("直接更新固件版本参数错误: versionId={}, message={}", id, e.getMessage());
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

    // ==================== CDN 预热相关接口 ====================

    /**
     * CDN 预热结果 DTO
     */
    @lombok.Data
    public static class CdnWarmResponse {
        /**
         * 版本 ID
         */
        private Long versionId;
        /**
         * 是否已触发预热
         */
        private Boolean triggered;
        /**
         * 消息
         */
        private String message;
        /**
         * 国际化消息 key
         */
        private String messageKey;
        /**
         * 预热策略
         */
        private String strategy;
        /**
         * 实际命中的 PoP
         */
        private String pop;
    }

    /**
     * 触发 CDN 预热
     * <p>
     * 通过 Cloudflare Workers 对固件进行 CDN 预热，
     * </p>
     *
     * @param id      版本 ID
     * @return 预热结果
     */
    @GetMapping("/{id}/warm")
    @PreAuthorize("@rbac.has('fota:firmware:update')")
    public ApiResponse<CdnWarmResponse> warmCdn(@PathVariable Long id) {
        if (id == null || id <= 0) {
            FirmwareWarmMessage.WarmMessage msg = FirmwareWarmMessage.INVALID_VERSION_ID;
            return ApiResponse.success(buildWarmFailureResponse(id, msg.messageKey(), msg.message()));
        }

        log.debug("开始 CDN 预热: versionId={}", id);

        try {
            var result = firmwarePublishService.warmCdnManually(id);
            CdnWarmResponse response = new CdnWarmResponse();
            response.setVersionId(result.versionId());
            response.setTriggered(result.triggered());
            response.setMessage(result.message());
            response.setMessageKey(result.messageKey());
            response.setStrategy(result.strategy());
            response.setPop(result.pop());
            return ApiResponse.success(response);
        } catch (BizException e) {
            log.warn("触发 CDN 预热失败: versionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            FirmwareWarmMessage.WarmMessage msg = FirmwareWarmMessage.fromMessage(e.getMessage());
            return ApiResponse.success(buildWarmFailureResponse(id, msg.messageKey(), msg.message()));
        } catch (IllegalArgumentException e) {
            log.warn("触发 CDN 预热参数错误: versionId={}, message={}", id, e.getMessage());
            FirmwareWarmMessage.WarmMessage msg = FirmwareWarmMessage.fromMessage(e.getMessage());
            return ApiResponse.success(buildWarmFailureResponse(id, msg.messageKey(), msg.message()));
        }
    }

    private CdnWarmResponse buildWarmFailureResponse(Long versionId, String messageKey, String message) {
        CdnWarmResponse response = new CdnWarmResponse();
        response.setVersionId(versionId);
        response.setTriggered(Boolean.FALSE);
        response.setMessageKey(messageKey);
        response.setMessage(message);
        return response;
    }

    /**
     * 创建并发布固件版本。
     * <p>
     * 异步执行：创建版本 → 转存文件 → CDN预热 → 激活版本
     * </p>
     *
     * @param reqDTO 发布请求参数
     * @return 任务ID和版本ID
     */
    @PostMapping("/publish")
    @PreAuthorize("@rbac.has('fota:firmware:create')")
    public ApiResponse<FirmwarePublishService.PublishResult> publishFirmwareVersion(
            @RequestBody @Valid FirmwareVersionReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        log.debug("创建并发布固件版本: productId={}, version={}, uploadSessionId={}",
                reqDTO.getProductId(), reqDTO.getVersion(), reqDTO.getUploadSessionId());

        try {
            FirmwareVersion publishDraft = firmwareVersionAssembler.toFirmwareVersionEntity(reqDTO);
            FirmwarePublishService.PublishResult result = firmwarePublishService.createAndPublish(
                    publishDraft,
                    reqDTO.getUploadSessionId()
            );

            log.info("固件版本发布任务已创建: taskId={}, versionId={}",
                    result.taskId(), result.versionId());

            return ApiResponse.success(result);
        } catch (BizException e) {
            log.warn("创建固件版本发布任务失败: productId={}, version={}, errorCode={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建固件版本发布任务参数错误: productId={}, version={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 更新并异步发布固件版本。
     */
    @PutMapping("/{id}/publish")
    @PreAuthorize("@rbac.has('fota:firmware:update')")
    public ApiResponse<FirmwarePublishService.PublishResult> updatePublishedFirmwareVersion(
            @PathVariable Long id,
            @RequestBody @Valid FirmwareVersionReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        log.debug("更新并发布固件版本: versionId={}, uploadSessionId={}", id, reqDTO.getUploadSessionId());

        try {
            FirmwareVersion publishDraft = firmwareVersionAssembler.toFirmwareVersionEntity(reqDTO);
            FirmwarePublishService.PublishResult result = firmwarePublishService.updateAndPublish(
                    id,
                    publishDraft,
                    reqDTO.getUploadSessionId()
            );
            return ApiResponse.success(result);
        } catch (BizException e) {
            log.warn("更新固件版本发布任务失败: versionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新固件版本发布任务参数错误: versionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

}
