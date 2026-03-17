package com.wewins.fota.application.reporting;

import com.wewins.fota.application.upgrade.GrayReleaseService;
import com.wewins.fota.application.upgrade.dto.CheckLogContext;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.common.enums.CheckMode;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 设备检查日志构建器
 * <p>
 * 将请求、设备、策略、结果等上下文组装成 DeviceCheckLog 实体
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCheckLogBuilder {

    private final GrayReleaseService grayReleaseService;

    /**
     * 构建设备检查日志
     *
     * @param request   升级检查请求
     * @param device   设备信息
     * @param policy   匹配的升级策略（可能为 null）
     * @param result  检查结果
     * @param logContext HTTP 请求上下文
     * @return DeviceCheckLog 实体
     */
    public DeviceCheckLog build(
            UpgradeCheckReqDTO request,
            Device device,
            UpgradePolicy policy,
            CheckResult result,
            CheckLogContext logContext) {

        // 获取灰度桶号
        int grayBucket = device != null && request.getImei() != null
                ? grayReleaseService.getBucketNumber(request.getImei())
                : 0;

        // 判断是否命中灰度
        int grayRate = policy != null ? policy.getGrayRate() : 0;
        boolean isGrayHit = policy != null
                && grayRate > 0
                && grayReleaseService.hitsGrayBucket(request.getImei(), grayRate);

        // 构建检查日志
        return DeviceCheckLog.builder()
                .requestId(result.getRequestId())
                .deviceId(device != null ? device.getId() : null)
                .productId(device != null ? device.getProductId() : null)
                .imei(request.getImei())
                .policyId(policy != null ? policy.getId() : null)
                .downloadUrl(result.getDownloadUrl())
                .version(request.getVersion())
                .internalVersion(request.getTag())
                .targetVersion(result.getTargetVersion())
                .targetInternalVersion(result.getTargetInternalVersion())
                .targetVersionId(result.getTargetVersionId())
                .checkRst(result.getDecision())
                .checkMode(request.getCheckMode() != null ? request.getCheckMode() : CheckMode.AUTO)
                .language(request.getLang())
                .isDev(request.getDev())
                .grayBucket(grayBucket)
                .isGrayHit(isGrayHit ? 1 : 0)
                .responseCheckInterval(result.getCheckInterval())
                .downloadDelay(result.getDownloadDelay())
                .eventTime(LocalDateTime.now(ZoneOffset.UTC))
                .clientIp(logContext != null ? logContext.getClientIp() : null)
                .userAgent(logContext != null ? logContext.getUserAgent() : null)
                .region(logContext != null ? logContext.getRegion() : null)
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .build();
    }
}
