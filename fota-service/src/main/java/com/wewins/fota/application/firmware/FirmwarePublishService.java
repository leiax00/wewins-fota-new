package com.wewins.fota.application.firmware;

import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.cdn.application.service.CdnWarmService;
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
 * 固件发布应用服务。
 * <p>
 * 编排固件版本发布流程：创建版本 → 转存文件 → CDN预热 → 激活版本
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwarePublishService {

    private static final String PACKAGE_STATUS_READY = "READY";
    private static final String PACKAGE_STATUS_PENDING = "PENDING";
    private static final String PACKAGE_STATUS_FAILED = "FAILED";
    private static final String BIZ_TYPE_FIRMWARE_PUBLISH = "FIRMWARE_PUBLISH";

    private final FirmwareVersionAppService firmwareVersionAppService;
    private final FirmwarePackagePreparationService firmwarePackagePreparationService;
    private final AsyncTaskService asyncTaskService;
    private final CdnWarmService cdnWarmService;
    private final SignedUrlService signedUrlService;

    /**
     * 创建并发布固件版本。
     *
     * @param publishDraft    已在上层完成 DTO 转换的固件版本草稿
     * @param uploadSessionId 上传会话 ID
     * @return 发布任务信息
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishResult createAndPublish(FirmwareVersion publishDraft, String uploadSessionId) {
        Long productId = publishDraft.getProductId();
        String version = publishDraft.getVersion();

        // 1. 校验上传会话并获取文件信息
        FirmwareUploadSession session = firmwarePackagePreparationService.loadAndValidateUploadSession(uploadSessionId, productId);

        // 2. 补齐草稿中的文件元信息，交给既有应用服务创建占位记录
        publishDraft.setId(null);
        publishDraft.setFileName(session.getFileName());
        publishDraft.setFileSize(session.getFileSize());
        publishDraft.setMd5(session.getMd5());
        publishDraft.setSha256(session.getSha256());
        publishDraft.setPackageStatus(PACKAGE_STATUS_PENDING);
        publishDraft.setPackageUploadedAt(null);

        FirmwareVersion createdVersion = firmwareVersionAppService.createFirmwareVersion(publishDraft);
        log.info("固件版本创建成功(待发布): firmwareVersionId={}, productId={}, version={}",
                createdVersion.getId(), productId, version);

        // 4. 创建异步任务
        Long taskId = asyncTaskService.createTask(TaskCreateCmd.builder()
                .bizType(BIZ_TYPE_FIRMWARE_PUBLISH)
                .bizId(createdVersion.getId().toString())
                .build());

        log.info("固件发布任务创建成功: taskId={}, firmwareVersionId={}", taskId, createdVersion.getId());

        // 5. 在事务提交后异步执行发布流程
        final Long versionId = createdVersion.getId();
        final String sessionId = uploadSessionId;

        // 使用事务同步确保在事务提交后才执行异步任务
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncTaskService.executeTask(taskId,
                        context -> executePublishFlow(context, versionId, sessionId, productId));
            }
        });

        return new PublishResult(taskId, createdVersion.getId());
    }

    /**
     * 执行发布流程。
     *
     * @param context          任务上下文
     * @param versionId        版本 ID
     * @param uploadSessionId  上传会话 ID
     * @param productId       产品 ID
     */
    private void executePublishFlow(TaskContext context, Long versionId, String uploadSessionId, Long productId) {
        try {
            // 阶段 1: 转存文件到对象存储 (10% -> 30%)
            context.updateProgress(TaskStage.PROCESSING, 10, "开始转存文件到对象存储");
            FirmwareUploadSession session = firmwarePackagePreparationService.loadAndValidateUploadSession(uploadSessionId, productId);
            String objectKey = firmwarePackagePreparationService.ensureObjectKeyReady(uploadSessionId, productId, session);
            context.updateProgress(TaskStage.PROCESSING, 30, "文件转存完成");

            // 阶段 2: 更新版本记录 (30% -> 50%)
            context.updateProgress(TaskStage.PROCESSING, 40, "更新版本记录");
            updateVersionToReady(versionId, objectKey, session);
            context.updateProgress(TaskStage.PROCESSING, 50, "版本记录已更新");

            // 阶段 3: CDN预热 (50% -> 80%) - 失败不阻断主流程
            context.updateProgress(TaskStage.PROCESSING, 60, "开始CDN预热");
            try {
                warmCdn(objectKey);
                context.updateProgress(TaskStage.PROCESSING, 80, "CDN预热完成");
            } catch (Exception e) {
                // CDN预热失败不阻断主流程，记录日志继续
                log.warn("CDN预热失败，不阻断主流程: versionId={}, error={}", versionId, e.getMessage());
                context.updateProgress(TaskStage.PROCESSING, 80, "CDN预热失败，已记录");
            }

            // 阶段 4: 完成 (100%)
            context.updateProgress(TaskStage.COMPLETED, 100, "固件发布完成");

            log.info("固件发布流程完成: versionId={}", versionId);

            // 5. 清理上传会话（在任务完成后删除）
            firmwarePackagePreparationService.cleanupUploadSession(uploadSessionId);

        } catch (Exception e) {
            log.error("固件发布流程失败: versionId={}", versionId, e);
            try {
                // 更新版本状态为 FAILED
                updateVersionToFailed(versionId);
            } catch (Exception updateEx) {
                log.error("更新版本状态失败: versionId={}", versionId, updateEx);
            }
            throw e;
        }
    }

    /**
     * 更新版本状态为 READY。
     */
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
        log.info("固件版本状态已更新为READY: versionId={}, objectKey={}", versionId, objectKey);
    }

    /**
     * 更新版本状态为 FAILED。
     */
    private void updateVersionToFailed(Long versionId) {
        FirmwareVersion version = firmwareVersionAppService.getById(versionId);
        version.setPackageStatus(PACKAGE_STATUS_FAILED);
        firmwareVersionAppService.updateFirmwareVersion(version);
    }

    /**
     * CDN预热。
     */
    private void warmCdn(String objectKey) {
        try {
            String downloadUrl = signedUrlService.generateSignedUrl(objectKey);

            if (downloadUrl != null && !downloadUrl.isBlank()) {
                cdnWarmService.warm(downloadUrl);
                // 只记录 objectKey，避免泄漏签名 URL
                log.info("CDN预热请求已发送: objectKey={}", objectKey);
            }
        } catch (Exception e) {
            log.warn("CDN预热失败: objectKey={}, error={}", objectKey, e.getMessage());
        }
    }

    /**
     * 发布结果。
     *
     * @param taskId    任务 ID
     * @param versionId 版本 ID
     */
    public record PublishResult(Long taskId, Long versionId) {
    }
}
