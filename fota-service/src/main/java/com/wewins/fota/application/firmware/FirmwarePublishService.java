package com.wewins.fota.application.firmware;

import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import com.wewins.fota.cdn.application.service.CdnWarmService;
import com.wewins.fota.cdn.application.dto.WarmResult;
import com.wewins.fota.cdn.domain.cdn.model.enums.CdnWarmStrategy;
import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.storage.core.FileTransferService;
import com.wewins.fota.task.application.dto.TaskCreateCmd;
import com.wewins.fota.task.application.service.AsyncTaskService;
import com.wewins.fota.task.application.task.TaskContext;
import com.wewins.fota.task.domain.task.model.enums.TaskStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 固件版本异步发布服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwarePublishService {

    private static final String PACKAGE_STATUS_NONE = "NONE";
    private static final String PACKAGE_STATUS_READY = "READY";
    private static final String PACKAGE_STATUS_PENDING = "PENDING";
    private static final String PACKAGE_STATUS_FAILED = "FAILED";
    private static final String BIZ_TYPE_FIRMWARE_PUBLISH = "FIRMWARE_PUBLISH";

    private final FirmwareVersionAppService firmwareVersionAppService;
    private final FirmwarePackagePreparationService firmwarePackagePreparationService;
    private final AsyncTaskService asyncTaskService;
    private final CdnWarmService cdnWarmService;
    private final SignedUrlService signedUrlService;
    private final FileTransferService fileTransferService;

    @Transactional(rollbackFor = Exception.class)
    public PublishResult createAndPublish(FirmwareVersion publishDraft, String uploadSessionId) {
        boolean hasPackage = hasText(uploadSessionId);

        if (hasPackage) {
            FirmwareUploadSession session = firmwarePackagePreparationService.loadAndValidateUploadSession(
                    uploadSessionId, publishDraft.getProductId());
            publishDraft.setFileName(session.getFileName());
            publishDraft.setFileSize(session.getFileSize());
            publishDraft.setMd5(session.getMd5());
            publishDraft.setSha256(session.getSha256());
            publishDraft.setPackageStatus(PACKAGE_STATUS_PENDING);
            publishDraft.setPackageUploadedAt(null);
        } else {
            publishDraft.setPackageStatus(resolveNoPackageStatus(publishDraft.getPackageStatus()));
            publishDraft.setPackageUploadedAt(null);
            clearPackageFields(publishDraft);
        }

        publishDraft.setId(null);
        FirmwareVersion createdVersion = firmwareVersionAppService.createFirmwareVersion(publishDraft);

        Long taskId = createPublishTask(createdVersion.getId());
        registerAfterCommit(taskId, context -> executeCreateFlow(
                context,
                createdVersion.getId(),
                createdVersion.getProductId(),
                uploadSessionId
        ));
        return new PublishResult(taskId, createdVersion.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PublishResult updateAndPublish(Long versionId, FirmwareVersion updateDraft, String uploadSessionId) {
        FirmwareVersion existingVersion = firmwareVersionAppService.getById(versionId);
        if (hasText(uploadSessionId)) {
            firmwarePackagePreparationService.loadAndValidateUploadSession(uploadSessionId, existingVersion.getProductId());
        }

        updateDraft.setId(versionId);
        updateDraft.setProductId(existingVersion.getProductId());
        updateDraft.setVersion(existingVersion.getVersion());
        updateDraft.setInternalVersion(existingVersion.getInternalVersion());

        Long taskId = createPublishTask(versionId);
        registerAfterCommit(taskId, context -> executeUpdateFlow(
                context,
                versionId,
                existingVersion,
                updateDraft,
                uploadSessionId
        ));
        return new PublishResult(taskId, versionId);
    }

    private Long createPublishTask(Long versionId) {
        Long taskId = asyncTaskService.createTask(TaskCreateCmd.builder()
                .bizType(BIZ_TYPE_FIRMWARE_PUBLISH)
                .bizId(versionId.toString())
                .build());
        log.info("固件发布任务创建成功: taskId={}, firmwareVersionId={}", taskId, versionId);
        return taskId;
    }

    private void registerAfterCommit(Long taskId, java.util.function.Consumer<TaskContext> consumer) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncTaskService.executeTask(taskId, consumer);
            }
        });
    }

    private void executeCreateFlow(TaskContext context, Long versionId, Long productId, String uploadSessionId) {
        if (!hasText(uploadSessionId)) {
            context.updateProgress(TaskStage.PROCESSING, 40, "开始创建无包版本");
            context.updateProgress(TaskStage.COMPLETED, 100, "固件版本处理完成");
            log.info("无包版本异步创建完成: versionId={}", versionId);
            return;
        }
        executePackageFlow(context, versionId, productId, uploadSessionId, null, null);
    }

    private void executeUpdateFlow(
            TaskContext context,
            Long versionId,
            FirmwareVersion existingVersion,
            FirmwareVersion updateDraft,
            String uploadSessionId
    ) {
        try {
            context.updateProgress(TaskStage.PROCESSING, 10, "开始更新版本信息");
            FirmwareVersion versionToSave = buildUpdatedVersion(existingVersion, updateDraft, uploadSessionId);
            firmwareVersionAppService.updateFirmwareVersion(versionToSave);
            context.updateProgress(TaskStage.PROCESSING, 30, "版本信息已更新");

            if (!hasText(uploadSessionId)) {
                context.updateProgress(TaskStage.COMPLETED, 100, "固件版本处理完成");
                log.info("固件版本异步更新完成: versionId={}", versionId);
                return;
            }

            executePackageFlow(
                    context,
                    versionId,
                    existingVersion.getProductId(),
                    uploadSessionId,
                    existingVersion.getFileUrl(),
                    existingVersion.getPackageStatus()
            );
        } catch (Exception e) {
            log.error("固件更新异步流程失败: versionId={}", versionId, e);
            throw e;
        }
    }

    private FirmwareVersion buildUpdatedVersion(FirmwareVersion existingVersion, FirmwareVersion updateDraft, String uploadSessionId) {
        FirmwareVersion version = firmwareVersionAppService.getById(existingVersion.getId());
        version.setTags(updateDraft.getTags());
        version.setMeta(updateDraft.getMeta());

        if (hasText(uploadSessionId)) {
            version.setPackageStatus(PACKAGE_STATUS_PENDING);
            version.setPackageUploadedAt(null);
        } else {
            version.setPackageStatus(resolveNoPackageStatus(existingVersion.getPackageStatus()));
            version.setPackageUploadedAt(existingVersion.getPackageUploadedAt());
            version.setFileUrl(existingVersion.getFileUrl());
            version.setFileName(existingVersion.getFileName());
            version.setFileSize(existingVersion.getFileSize());
            version.setMd5(existingVersion.getMd5());
            version.setSha256(existingVersion.getSha256());
        }
        return version;
    }

    private void executePackageFlow(
            TaskContext context,
            Long versionId,
            Long productId,
            String uploadSessionId,
            String oldObjectKey,
            String previousPackageStatus
    ) {
        try {
            context.updateProgress(TaskStage.PROCESSING, 40, "开始转存文件到对象存储");
            FirmwareUploadSession session = firmwarePackagePreparationService.loadAndValidateUploadSession(uploadSessionId, productId);
            String objectKey = firmwarePackagePreparationService.ensureObjectKeyReady(uploadSessionId, productId, session);
            context.updateProgress(TaskStage.PROCESSING, 60, "文件转存完成");

            updateVersionToReady(versionId, objectKey, session);
            context.updateProgress(TaskStage.PROCESSING, 75, "版本记录已更新");

            try {
                context.updateProgress(TaskStage.PROCESSING, 85, "开始CDN预热");
                warmCdn(objectKey);
                context.updateProgress(TaskStage.PROCESSING, 95, "CDN预热完成");
            } catch (Exception e) {
                log.warn("CDN预热失败，不阻断主流程: versionId={}, error={}", versionId, e.getMessage());
                context.updateProgress(TaskStage.PROCESSING, 95, "CDN预热失败，已记录");
            }

            firmwarePackagePreparationService.cleanupUploadSession(uploadSessionId);
            cleanupReplacedObject(oldObjectKey, objectKey, versionId);
            context.updateProgress(TaskStage.COMPLETED, 100, "固件版本处理完成");
            log.info("固件包异步处理完成: versionId={}, objectKey={}", versionId, objectKey);
        } catch (Exception e) {
            log.error("固件包异步流程失败: versionId={}", versionId, e);
            try {
                if (PACKAGE_STATUS_READY.equalsIgnoreCase(previousPackageStatus)) {
                    FirmwareVersion version = firmwareVersionAppService.getById(versionId);
                    version.setPackageStatus(PACKAGE_STATUS_READY);
                    firmwareVersionAppService.updateFirmwareVersion(version);
                } else {
                    updateVersionToFailed(versionId);
                }
            } catch (Exception updateEx) {
                log.error("回滚固件包状态失败: versionId={}", versionId, updateEx);
            }
            throw e;
        }
    }

    private void updateVersionToReady(Long versionId, String objectKey, FirmwareUploadSession session) {
        FirmwareVersion version = firmwareVersionAppService.getById(versionId);
        version.setFileUrl(objectKey);
        version.setFileName(session.getFileName());
        version.setFileSize(session.getFileSize());
        version.setMd5(session.getMd5());
        version.setSha256(session.getSha256());
        version.setPackageStatus(PACKAGE_STATUS_READY);
        version.setPackageUploadedAt(LocalDateTime.now());
        firmwareVersionAppService.updateFirmwareVersion(version);
    }

    private void updateVersionToFailed(Long versionId) {
        FirmwareVersion version = firmwareVersionAppService.getById(versionId);
        version.setPackageStatus(PACKAGE_STATUS_FAILED);
        firmwareVersionAppService.updateFirmwareVersion(version);
    }

    private void warmCdn(String objectKey) {
        String downloadUrl = signedUrlService.generateSignedUrl(objectKey);
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            WarmResult result = cdnWarmService.warmByConfiguredStrategy(downloadUrl);
            log.info("CDN预热请求已发送: objectKey={}, strategy={}, success={}, pop={}, cfRay={}, message={}",
                    objectKey, result.getStrategy(), isSuccess(result), result.getPop(), result.getCfRay(), resolveWarmMessage(result));
        }
    }

    private void cleanupReplacedObject(String oldObjectKey, String newObjectKey, Long firmwareVersionId) {
        if (!hasText(oldObjectKey) || oldObjectKey.equals(newObjectKey)) {
            return;
        }
        try {
            fileTransferService.deleteStorageObject(oldObjectKey);
            log.info("清理旧固件包成功: firmwareVersionId={}, oldObjectKey={}", firmwareVersionId, oldObjectKey);
        } catch (RuntimeException e) {
            log.error("清理旧固件包失败（将产生孤儿文件）: firmwareVersionId={}, oldObjectKey={}",
                    firmwareVersionId, oldObjectKey, e);
        }
    }

    private void clearPackageFields(FirmwareVersion version) {
        version.setFileUrl(null);
        version.setFileName(null);
        version.setFileSize(null);
        version.setMd5(null);
        version.setSha256(null);
    }

    private String resolveNoPackageStatus(String packageStatus) {
        return PACKAGE_STATUS_NONE.equalsIgnoreCase(packageStatus) ? PACKAGE_STATUS_NONE : packageStatus;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public ManualWarmResult warmCdnManually(Long versionId) {
        FirmwareVersion version = firmwareVersionAppService.getById(versionId);
        if (!PACKAGE_STATUS_READY.equals(version.getPackageStatus())) {
            throw new IllegalArgumentException("该固件版本没有可用的包，无法进行预热");
        }

        String objectKey = version.getFileUrl();
        if (!hasText(objectKey)) {
            throw new IllegalArgumentException("固件包路径缺失");
        }

        String downloadUrl = signedUrlService.generateSignedUrl(objectKey);
        if (!hasText(downloadUrl)) {
            throw new IllegalArgumentException("无法生成下载地址");
        }

        WarmResult warmResult = cdnWarmService.warmByConfiguredStrategy(downloadUrl);
        return new ManualWarmResult(
                versionId,
                isSuccess(warmResult),
                resolveManualWarmMessageKey(warmResult),
                warmResult.getStrategy() == null ? null : warmResult.getStrategy().name(),
                warmResult.getPop(),
                buildManualWarmMessage(warmResult)
        );
    }

    private String buildManualWarmMessage(WarmResult result) {
        String message = resolveWarmMessage(result);
        if (hasText(result.getPop())) {
            message = message + "，实际PoP=" + result.getPop();
        }
        if (result.getStrategy() == CdnWarmStrategy.WORKER) {
            return message;
        }
        return "已按服务器预热方式执行: " + message;
    }

    private boolean isSuccess(WarmResult result) {
        return result != null && result.getStatus() == WarmStatus.SUCCESS;
    }

    private String resolveWarmMessage(WarmResult result) {
        if (result == null) {
            return "预热失败";
        }
        if (isSuccess(result)) {
            return "预热成功";
        }
        return hasText(result.getErrorMessage()) ? result.getErrorMessage() : "预热失败";
    }

    private String resolveManualWarmMessageKey(WarmResult result) {
        if (isSuccess(result)) {
            return result.getStrategy() == CdnWarmStrategy.WORKER
                    ? "firmware.warm.successWorker"
                    : "firmware.warm.successServer";
        }
        return "firmware.warm.failed";
    }

    public record PublishResult(Long taskId, Long versionId) {
    }

    public record ManualWarmResult(
            Long versionId,
            boolean triggered,
            String messageKey,
            String strategy,
            String pop,
            String message
    ) {
    }
}
