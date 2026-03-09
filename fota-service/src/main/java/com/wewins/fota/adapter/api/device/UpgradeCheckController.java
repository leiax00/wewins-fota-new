package com.wewins.fota.adapter.api.device;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.wewins.fota.adapter.api.device.dto.UpgradeCheckRespDTO;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.upgrade.UpgradeCheckService;
import com.wewins.fota.application.upgrade.dto.CheckLogContext;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.util.HttpUtils;
import com.wewins.fota.infra.sentinel.UpgradeCheckBlockHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 设备升级检查控制器
 * <p>
 * 提供 /v1/upgrade/check 接口，供设备检查是否有可用更新
 * </p>
 * <p>
 * 分层职责：
 * <ul>
 *   <li>Controller 负责接收请求、返回响应</li>
 *   <li>Controller 负责将 Application 层的 CheckResult 转换为 API 层的 UpgradeCheckRespDTO</li>
 *   <li>CheckResult 包含业务决策信息（内部状态）</li>
 *   <li>UpgradeCheckRespDTO 严格按 PRD 定义（对外格式）</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@RestController
@ConditionalOnAppMode({"main", "region"})
@RequiredArgsConstructor
public class UpgradeCheckController {

    private final UpgradeCheckService upgradeCheckService;
    private final UpgradeCheckBlockHandler blockHandler;

    @Value("${app.node.code:main}")
    private String region;

    /**
     * 检查设备升级（GET 方法）
     */
    @GetMapping("/v1/upgrade/check")
    @SentinelResource(value = "upgrade:check", blockHandler = "handleBlock", fallback = "handleFallback")
    public ResponseEntity<UpgradeCheckRespDTO> checkUpgrade(
            @ModelAttribute UpgradeCheckReqDTO request,
            HttpServletRequest httpRequest) {
        log.debug("收到设备检查 GET 请求: {}", request);
        CheckLogContext logContext = buildLogContext(httpRequest);
        CheckResult result = upgradeCheckService.checkUpgrade(request, logContext);
        UpgradeCheckRespDTO response = convertToRespDTO(result);
        return ResponseEntity.ok(response);
    }

    /**
     * Sentinel BlockHandler - 方法签名必须与原方法一致（最后追加 BlockException）
     */
    public ResponseEntity<UpgradeCheckRespDTO> handleBlock(
            UpgradeCheckReqDTO request,
            HttpServletRequest httpRequest,
            com.alibaba.csp.sentinel.slots.block.BlockException ex) {
        return ResponseEntity.ok(blockHandler.handleBlock(request, ex));
    }

    /**
     * Sentinel Fallback - 方法签名必须与原方法一致（最后追加 Throwable）
     */
    public ResponseEntity<UpgradeCheckRespDTO> handleFallback(
            UpgradeCheckReqDTO request,
            HttpServletRequest httpRequest,
            Throwable t) {
        log.error("Fallback triggered for upgrade check: imei={}", request.getImei(), t);
        return ResponseEntity.ok(UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.ERROR.getCode())
                .requestId(java.util.UUID.randomUUID().toString())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(3600)
                        .downloadDelay(0)
                        .build())
                .build());
    }

    /**
     * 兼容老系统的版本检测
     * <p>
     * 老接口参数: imei, version, language, tags
     * </p>
     */
    @GetMapping("/fota/version/query")
    @SentinelResource(value = "upgrade:check", blockHandler = "handleBlock", fallback = "handleFallback")
    public ResponseEntity<UpgradeCheckRespDTO> checkUpgradeOld(
            @ModelAttribute UpgradeCheckReqDTO request,
            HttpServletRequest httpRequest) {
        log.debug("[Legacy API] 收到设备检查 GET 请求: {}", request);
        CheckLogContext logContext = buildLogContext(httpRequest);
        CheckResult result = upgradeCheckService.checkUpgrade(request, logContext);
        UpgradeCheckRespDTO response = convertToRespDTO(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 检查设备升级（POST 方法）
     */
    @PostMapping("/v1/upgrade/check")
    @SentinelResource(value = "upgrade:check", blockHandler = "handleBlock", fallback = "handleFallback")
    public ResponseEntity<UpgradeCheckRespDTO> checkUpgradePost(
            @RequestBody UpgradeCheckReqDTO request,
            HttpServletRequest httpRequest) {
        log.debug("收到设备检查 POST 请求: {}", request);
        CheckLogContext logContext = buildLogContext(httpRequest);
        CheckResult result = upgradeCheckService.checkUpgrade(request, logContext);
        UpgradeCheckRespDTO response = convertToRespDTO(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 构建检查日志上下文
     *
     * @param httpRequest HTTP 请求
     * @return 日志上下文
     */
    private CheckLogContext buildLogContext(HttpServletRequest httpRequest) {
        return CheckLogContext.builder()
                .clientIp(HttpUtils.extractClientIp(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .region(region)
                .build();
    }

    /**
     * 将 Application 层的 CheckResult 转换为 API 层的 UpgradeCheckRespDTO
     * <p>
     * CheckResult 包含业务决策信息和内部状态
     * UpgradeCheckRespDTO 严格按照 PRD 定义的响应格式
     * </p>
     *
     * @param result Application 层返回的检查结果
     * @return PRD 标准响应 DTO
     */
    private UpgradeCheckRespDTO convertToRespDTO(CheckResult result) {
        if (result == null) {
            return buildEmptyResponse();
        }

        // 根据 decision 决定如何构建响应
        UpgradeDecision decision = result.getDecision();
        if (decision == null) {
            decision = UpgradeDecision.ERROR;
        }

        return switch (decision) {
            case UPDATE -> buildUpdateResponse(result);
            case NO_UPDATE -> buildNoUpdateResponse(result);
            case RATE_LIMITED -> buildRateLimitedResponse(result);
            case DEVICE_NOT_FOUND -> buildNotFoundResponse(result);
            case ERROR -> buildErrorResponse(result);
        };
    }

    /**
     * 构建"有更新"响应（包含完整的固件信息）
     */
    private UpgradeCheckRespDTO buildUpdateResponse(CheckResult result) {
        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.UPDATE.getCode())
                .requestId(result.getRequestId())
                .releaseStartDate(result.getReleaseStartDate())
                .releaseNote(result.getReleaseNote())
                .newFirmware(result.getNewFirmware())
                .downloadUrl(result.getDownloadUrl())
                .fileSize(result.getFileSize())
                .fileSizeText(result.getFileSizeText())
                .checksum(result.getChecksum())
                .checksumType(result.getChecksumType())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(result.getCheckInterval())
                        .downloadDelay(result.getDownloadDelay())
                        .build())
                .build();
    }

    /**
     * 构建"无更新"响应（仅包含 code 和 control）
     */
    private UpgradeCheckRespDTO buildNoUpdateResponse(CheckResult result) {
        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.NO_UPDATE.getCode())
                .requestId(result.getRequestId())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(result.getCheckInterval() != null ? result.getCheckInterval() : 86400)
                        .downloadDelay(0)
                        .build())
                .build();
    }

    /**
     * 构建"限流"响应（仅包含 code 和 control）
     */
    private UpgradeCheckRespDTO buildRateLimitedResponse(CheckResult result) {
        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.RATE_LIMITED.getCode())
                .requestId(result.getRequestId())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(result.getCheckInterval())
                        .downloadDelay(result.getDownloadDelay())
                        .build())
                .build();
    }

    /**
     * 构建"设备不存在"响应
     */
    private UpgradeCheckRespDTO buildNotFoundResponse(CheckResult result) {
        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.DEVICE_NOT_FOUND.getCode())
                .requestId(result.getRequestId())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(result.getCheckInterval() != null ? result.getCheckInterval() : 3600)
                        .downloadDelay(0)
                        .build())
                .build();
    }

    /**
     * 构建"错误"响应
     */
    private UpgradeCheckRespDTO buildErrorResponse(CheckResult result) {
        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.ERROR.getCode())
                .requestId(result.getRequestId())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(result.getCheckInterval() != null ? result.getCheckInterval() : 3600)
                        .downloadDelay(0)
                        .build())
                .build();
    }

    /**
     * 构建空响应
     * <p>
     * 当 CheckResult 为 null 时使用，生成新的 requestId 用于链路追踪
     * </p>
     */
    private UpgradeCheckRespDTO buildEmptyResponse() {
        return UpgradeCheckRespDTO.builder()
                .code(UpgradeDecision.ERROR.getCode())
                .requestId(com.wewins.fota.common.util.IdGenerator.simpleUUID())
                .control(UpgradeCheckRespDTO.Control.builder()
                        .checkInterval(3600)
                        .downloadDelay(0)
                        .build())
                .build();
    }
}
