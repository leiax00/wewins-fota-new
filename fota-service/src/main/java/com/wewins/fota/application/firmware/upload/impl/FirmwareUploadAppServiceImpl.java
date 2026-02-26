package com.wewins.fota.application.firmware.upload.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.firmware.upload.FirmwareUploadAppService;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import com.wewins.fota.infra.validation.FirmwareFileValidator;
import com.wewins.fota.storage.core.FileTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * 固件上传应用服务实现。
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@Service
public class FirmwareUploadAppServiceImpl implements FirmwareUploadAppService {

    private static final int BUFFER_SIZE = 16 * 1024; // 16KB

    /**
     * Lua脚本：原子性地获取并删除key（GETDEL）
     * <p>
     * 如果key存在，返回值并删除key；如果key不存在，返回false
     * </p>
     */
    private static final String GET_AND_DELETE_SCRIPT =
            "local value = redis.call('GET', KEYS[1]); " +
            "if value then " +
            "    redis.call('DEL', KEYS[1]); " +
            "    return value; " +
            "else " +
            "    return false; " +
            "end";

    private final FileTransferService fileTransferService;
    private final FirmwareFileValidator firmwareFileValidator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Path stagingTempDir;

    /**
     * 构造函数。
     *
     * @param fileTransferService     文件传输服务
     * @param firmwareFileValidator   文件校验器
     * @param redisTemplate           Redis模板（统一使用）
     * @param objectMapper             Jackson对象映射器
     * @param stagingTempDir          临时文件目录（用于路径安全校验）
     */
    public FirmwareUploadAppServiceImpl(
            FileTransferService fileTransferService,
            FirmwareFileValidator firmwareFileValidator,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("storageLocalTempDir") Path stagingTempDir) {
        this.fileTransferService = fileTransferService;
        this.firmwareFileValidator = firmwareFileValidator;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.stagingTempDir = stagingTempDir;
    }

    @Override
    public FirmwareUploadSession uploadToFirmwareStaging(MultipartFile file, Long productId) {
        // 1. 参数校验
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "上传文件不能为空");
        }
        if (productId == null || productId <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }

        // 2. 文件类型校验
        FirmwareFileValidator.ValidationResult validationResult;
        try {
            validationResult = firmwareFileValidator.validate(file);
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "读取文件内容失败", e);
        }
        if (!validationResult.isPassed()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), validationResult.getErrorMessage());
        }

        // 3. 生成会话 ID
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String redisKey = String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, sessionId);
        LocalDateTime now = LocalDateTime.now();

        // 4. 写入临时文件并计算哈希
        Path stagingPath = null;
        try {
            stagingPath = fileTransferService.createStagingFile(
                    "fw-" + productId + "-",
                    ".upload"
            );

            HashDigestResult digest = writeToStagingAndDigest(file, stagingPath);

            // 5. 创建会话对象
            FirmwareUploadSession session = FirmwareUploadSession.builder()
                    .sessionId(sessionId)
                    .status(FirmwareUploadSession.UploadStatus.UPLOADED)
                    .productId(productId)
                    .version(null)
                    .fileName(file.getOriginalFilename())
                    .fileSize(validationResult.getFileSize())
                    .md5(digest.md5())
                    .sha256(digest.sha256())
                    .mime(validationResult.getDetectedMime())
                    .tempPath(stagingPath.toString())
                    .objectKey(null)
                    .createdAt(now)
                    .updatedAt(now)
                    .lastError(null)
                    .build();

            // 6. 保存到 Redis（TTL 2h）- 统一使用通用 RedisTemplate（已启用 default typing）
            redisTemplate.opsForValue().set(
                    redisKey,
                    session,
                    RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_TTL_SECONDS,
                    java.util.concurrent.TimeUnit.SECONDS
            );

            if (log.isDebugEnabled()) {
                log.debug("固件上传会话创建成功: sessionId={}, productId={}, fileName={}, size={}",
                        sessionId, productId, file.getOriginalFilename(), validationResult.getFileSize());
            }
            return session;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 失败补偿：清理临时文件
            if (stagingPath != null) {
                try {
                    fileTransferService.deleteStagingFile(stagingPath);
                } catch (Exception ex) {
                    log.warn("清理临时文件失败: path={}", stagingPath, ex);
                }
            }
            log.error("上传固件到临时目录失败: productId={}", productId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "上传固件文件失败", e);
        }
    }

    @Override
    public FirmwareUploadSession getUploadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }

        String redisKey = String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, sessionId);

        // 统一使用通用 RedisTemplate 读取（已启用 default typing，自动反序列化）
        FirmwareUploadSession session = (FirmwareUploadSession) redisTemplate.opsForValue().get(redisKey);

        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "上传会话不存在或已过期");
        }

        return session;
    }

    @Override
    public FirmwareUploadSession consumeUploadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }

        String redisKey = String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, sessionId);

        // 使用Lua脚本原子性地获取并删除key（GETDEL），避免并发竞态
        DefaultRedisScript<Object> script = new DefaultRedisScript<>();
        script.setScriptText(GET_AND_DELETE_SCRIPT);
        script.setResultType(Object.class);

        Object result = redisTemplate.execute(script, Collections.singletonList(redisKey));

        if (result == null || Boolean.FALSE.equals(result)) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "上传会话不存在或已被消费");
        }

        // 处理返回值（可能是 FirmwareUploadSession 或 LinkedHashMap）
        FirmwareUploadSession session;
        if (result instanceof FirmwareUploadSession) {
            session = (FirmwareUploadSession) result;
        } else if (result instanceof Map) {
            // 兜底：处理存量 LinkedHashMap 数据（序列化配置未生效前的数据）
            log.warn("上传会话数据类型为 Map，进行兜底转换: sessionId={}", sessionId);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) result;
                session = objectMapper.convertValue(map, FirmwareUploadSession.class);
            } catch (Exception e) {
                log.error("转换上传会话 Map 到对象失败: sessionId={}", sessionId, e);
                throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "上传会话数据格式错误");
            }
        } else {
            log.error("上传会话返回类型异常: expected={}, actual={}, redisKey={}",
                    FirmwareUploadSession.class.getName(),
                    result.getClass().getName(),
                    redisKey);
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "上传会话数据格式错误");
        }

        if (log.isDebugEnabled()) {
            log.debug("固件上传会话已消费: sessionId={}, productId={}", sessionId, session.getProductId());
        }
        return session;
    }

    @Override
    public boolean cancelUploadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }

        String redisKey = String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, sessionId);

        // 统一使用通用 RedisTemplate 读取（已启用 default typing，自动反序列化）
        FirmwareUploadSession session = (FirmwareUploadSession) redisTemplate.opsForValue().get(redisKey);

        if (session == null) {
            return false; // 会话不存在，视为清理失败
        }

        // 删除临时文件（带路径安全校验）
        boolean fileDeleted = true;
        String tempPath = session.getTempPath();
        if (tempPath != null && !tempPath.isBlank()) {
            try {
                // 路径安全校验：标准化路径后检查是否在 staging 目录下（防止路径遍历攻击）
                Path normalizedStagingDir = stagingTempDir.toAbsolutePath().normalize();
                Path normalizedTargetPath = Paths.get(tempPath).toAbsolutePath().normalize();
                if (!normalizedTargetPath.startsWith(normalizedStagingDir)) {
                    log.error("临时文件路径不在staging目录下，拒绝删除: sessionId={}, tempPath={}",
                            sessionId, tempPath);
                    fileDeleted = false;
                } else {
                    fileTransferService.deleteStagingFile(normalizedTargetPath);
                }
            } catch (Exception e) {
                log.warn("删除临时文件失败: sessionId={}, tempPath={}", sessionId, tempPath, e);
                fileDeleted = false;
            }
        }

        // 只有在文件删除成功（或没有文件）时才删除 Redis 会话
        // 这样如果文件删除失败，会话仍保留，可以通过后台任务重试清理
        if (fileDeleted) {
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception e) {
                log.warn("删除上传会话缓存失败: sessionId={}", sessionId, e);
                return false;
            }
        } else {
            // 文件删除失败，保留会话并记录错误，标记为需要清理
            log.warn("保留会话以便后续清理: sessionId={}, tempPath={}", sessionId, tempPath);
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("上传会话已取消: sessionId={}, fileDeleted={}", sessionId, fileDeleted);
        }
        return true;
    }

    /**
     * 单次流式遍历完成"写入临时文件 + 计算 MD5/SHA-256"。
     *
     * @param file        上传文件
     * @param stagingPath 临时文件路径
     * @return 哈希结果（MD5 + SHA-256）
     */
    private HashDigestResult writeToStagingAndDigest(MultipartFile file, Path stagingPath) {
        MessageDigest md5Digest = initDigest("MD5");
        MessageDigest sha256Digest = initDigest("SHA-256");

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream inputStream = file.getInputStream();
             var outputStream = Files.newOutputStream(stagingPath)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                md5Digest.update(buffer, 0, read);
                sha256Digest.update(buffer, 0, read);
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "写入临时文件失败", e);
        }

        return new HashDigestResult(
                HexFormat.of().formatHex(md5Digest.digest()),
                HexFormat.of().formatHex(sha256Digest.digest())
        );
    }

    /**
     * 初始化摘要算法。
     *
     * @param algorithm 算法名称（MD5、SHA-256）
     * @return MessageDigest 实例
     */
    private MessageDigest initDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(),
                    "初始化摘要算法失败: " + algorithm, e);
        }
    }

    /**
     * 哈希结果记录。
     *
     * @param md5    MD5 哈希值（32 位十六进制）
     * @param sha256 SHA-256 哈希值（64 位十六进制）
     */
    private record HashDigestResult(String md5, String sha256) {
    }

    @Override
    public void saveUploadSession(FirmwareUploadSession session) {
        if (session == null || session.getSessionId() == null || session.getSessionId().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }
        String redisKey = String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, session.getSessionId());
        redisTemplate.opsForValue().set(
                redisKey,
                session,
                RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        );
        if (log.isDebugEnabled()) {
            log.debug("上传会话已保存: sessionId={}, objectKey={}", session.getSessionId(), session.getObjectKey());
        }
    }

    @Override
    public boolean deleteUploadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sessionId 不能为空");
        }
        String redisKey = String.format(RedisKeyConstants.FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE, sessionId);
        try {
            Boolean deleted = redisTemplate.delete(redisKey);
            if (log.isDebugEnabled()) {
                log.debug("上传会话已删除: sessionId={}, deleted={}", sessionId, deleted);
            }
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.warn("删除上传会话缓存失败: sessionId={}", sessionId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "删除上传会话失败", e);
        }
    }
}
