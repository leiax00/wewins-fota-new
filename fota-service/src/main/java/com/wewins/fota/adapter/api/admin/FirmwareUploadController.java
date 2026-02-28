package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.firmware.AttachPackageReqDTO;
import com.wewins.fota.adapter.api.admin.dto.firmware.UploadSessionDetailDTO;
import com.wewins.fota.adapter.api.admin.dto.firmware.UploadSessionResponseDTO;
import com.wewins.fota.application.firmware.upload.FirmwareUploadAppService;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import com.wewins.fota.infra.validation.FirmwareFileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 固件上传控制器。
 * <p>
 * 提供固件包上传的 REST API，支持两阶段上传流程：
 * <ol>
 *   <li>POST /api/admin/firmware-uploads - 上传文件到临时目录</li>
 *   <li>GET /api/admin/firmware-uploads/{sessionId} - 查询上传会话状态</li>
 *   <li>DELETE /api/admin/firmware-uploads/{sessionId} - 取消上传并清理临时文件</li>
 * </ol>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/firmware-uploads")
@RequiredArgsConstructor
public class FirmwareUploadController {

    private final FirmwareUploadAppService firmwareUploadAppService;
    private final FirmwareFileValidator firmwareFileValidator;

    /**
     * 上传固件包到临时目录。
     * <p>
     * 上传流程：
     * <ol>
     *   <li>校验文件类型和大小（200MB 上限）</li>
     *   <li>写入临时文件，计算 MD5/SHA-256</li>
     *   <li>创建上传会话（Redis TTL 2h）</li>
     *   <li>返回会话 ID 和文件元数据</li>
     * </ol>
     * </p>
     *
     * @param file      上传文件
     * @param productId 关联的产品 ID
     * @return 上传成功响应（包含 sessionId、MD5、SHA-256、文件大小等）
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:firmware:create')")
    public ApiResponse<UploadSessionResponseDTO> uploadFirmware(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productId") Long productId) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "上传文件不能为空");
        }
        if (productId == null || productId <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        try {
            // 文件类型校验
            FirmwareFileValidator.ValidationResult validationResult;
            try {
                validationResult = firmwareFileValidator.validate(file);
            } catch (IOException e) {
                log.warn("文件类型校验失败: productId={}, fileName={}",
                        productId, file.getOriginalFilename(), e);
                return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(),
                        "读取文件内容失败");
            }
            if (!validationResult.isPassed()) {
                return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(),
                        validationResult.getErrorMessage());
            }

            // 上传到临时目录
            FirmwareUploadSession session = firmwareUploadAppService
                    .uploadToFirmwareStaging(file, productId);

            UploadSessionResponseDTO response = UploadSessionResponseDTO.builder()
                    .sessionId(session.getSessionId())
                    .status(session.getStatus().name())
                    .productId(session.getProductId())
                    .fileName(session.getFileName())
                    .fileSize(session.getFileSize())
                    .md5(session.getMd5())
                    .sha256(session.getSha256())
                    .mime(session.getMime())
                    .build();

            log.info("固件上传成功: sessionId={}, productId={}, fileName={}",
                    session.getSessionId(), productId, file.getOriginalFilename());
            return ApiResponse.success(response);
        } catch (BizException e) {
            log.warn("固件上传失败: productId={}, errorCode={}, message={}",
                    productId, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("固件上传异常: productId={}", productId, e);
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                    ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    /**
     * 查询上传会话状态。
     * <p>
     * 用于前端轮询上传状态或恢复会话。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 上传会话详情
     */
    @GetMapping("/{sessionId}")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<UploadSessionDetailDTO> getUploadSession(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }

        try {
            FirmwareUploadSession session = firmwareUploadAppService.getUploadSession(sessionId);

            UploadSessionDetailDTO response = UploadSessionDetailDTO.builder()
                    .sessionId(session.getSessionId())
                    .status(session.getStatus().name())
                    .productId(session.getProductId())
                    .version(session.getVersion())
                    .fileName(session.getFileName())
                    .fileSize(session.getFileSize())
                    .md5(session.getMd5())
                    .sha256(session.getSha256())
                    .mime(session.getMime())
                    .objectKey(session.getObjectKey())
                    .createdAt(session.getCreatedAt())
                    .updatedAt(session.getUpdatedAt())
                    .lastError(session.getLastError())
                    .build();
            return ApiResponse.success(response);
        } catch (BizException e) {
            log.warn("查询上传会话失败: sessionId={}, errorCode={}, message={}",
                    sessionId, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * 取消上传并清理临时文件。
     * <p>
     * 用于用户主动取消上传或清理超时会话。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 空（成功时返回 200 OK）
     */
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("@rbac.has('fota:firmware:delete')")
    public ApiResponse<Void> cancelUploadSession(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }

        try {
            boolean result = firmwareUploadAppService.cancelUploadSession(sessionId);
            if (!result) {
                return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(),
                        "上传会话不存在或清理失败");
            }
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("取消上传会话失败: sessionId={}, errorCode={}, message={}",
                    sessionId, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }
}
