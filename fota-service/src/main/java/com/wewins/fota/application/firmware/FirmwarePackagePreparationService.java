package com.wewins.fota.application.firmware;

import com.wewins.fota.application.firmware.dto.FirmwareVersionReqDTO;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.storage.core.FileTransferService;
import com.wewins.fota.storage.naming.StorageObjectKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 固件包准备服务。
 * <p>
 * 统一处理上传会话校验、对象存储转存和成功后的会话清理，
 * 避免控制器与异步发布流程各自复制一套上传处理逻辑。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwarePackagePreparationService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final FirmwareUploadAppService firmwareUploadAppService;
    private final FileTransferService fileTransferService;
    private final StorageObjectKeyGenerator storageObjectKeyGenerator;

    public ProcessedUploadSession processUploadSession(FirmwareVersionReqDTO reqDTO) {
        if (reqDTO.getUploadSessionId() == null || reqDTO.getUploadSessionId().isBlank()) {
            return new ProcessedUploadSession(reqDTO, null);
        }

        String sessionId = reqDTO.getUploadSessionId();
        FirmwareUploadSession session = loadAndValidateUploadSession(sessionId, reqDTO.getProductId());
        String objectKey = ensureObjectKeyReady(sessionId, reqDTO.getProductId(), session);

        FirmwareVersionReqDTO processed = new FirmwareVersionReqDTO();
        processed.setProductId(reqDTO.getProductId());
        processed.setVersion(reqDTO.getVersion());
        processed.setInternalVersion(reqDTO.getInternalVersion());
        processed.setFileUrl(objectKey);
        processed.setFileName(session.getFileName());
        processed.setFileSize(session.getFileSize());
        processed.setMd5(session.getMd5());
        processed.setSha256(session.getSha256());
        processed.setTags(reqDTO.getTags());
        processed.setMeta(reqDTO.getMeta());

        return new ProcessedUploadSession(processed, sessionId);
    }

    public FirmwareUploadSession loadAndValidateUploadSession(String sessionId, Long expectedProductId) {
        FirmwareUploadSession session = firmwareUploadAppService.getUploadSession(sessionId);
        if (session.getProductId() != null && !session.getProductId().equals(expectedProductId)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "上传会话与产品不匹配");
        }
        return session;
    }

    public String ensureObjectKeyReady(String sessionId, Long productId, FirmwareUploadSession session) {
        if (session.getObjectKey() != null && !session.getObjectKey().isBlank()) {
            return session.getObjectKey();
        }

        if (session.getTempPath() == null || session.getTempPath().isBlank()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(),
                    "上传会话文件信息不完整：缺少临时路径和对象Key");
        }

        Path tempPath = Path.of(session.getTempPath());
        try {
            String objectKey = storageObjectKeyGenerator.generateFirmwarePackageKey(productId, session.getFileName());
            fileTransferService.transferToStorage(
                    tempPath,
                    objectKey,
                    session.getMime() != null ? session.getMime() : DEFAULT_CONTENT_TYPE,
                    true
            );

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

    public void cleanupUploadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            boolean deleted = firmwareUploadAppService.deleteUploadSession(sessionId);
            if (!deleted) {
                log.warn("上传会话删除失败或已不存在: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.warn("清理上传会话失败: sessionId={}", sessionId, e);
        }
    }

    public record ProcessedUploadSession(
            FirmwareVersionReqDTO requestDTO,
            String uploadSessionId
    ) {
    }
}
