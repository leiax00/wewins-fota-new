package com.wewins.fota.application.firmware.upload;

import com.wewins.fota.cache.dto.FirmwareUploadSession;
import org.springframework.web.multipart.MultipartFile;

/**
 * 固件上传应用服务接口。
 * <p>
 * 提供固件包上传的会话管理功能，支持两阶段上传流程：
 * <ol>
 *   <li>上传文件到临时目录，创建会话</li>
 *   <li>查询、消费或取消上传会话</li>
 * </ol>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public interface FirmwareUploadAppService {

    /**
     * 上传文件到本地临时目录并创建会话。
     * <p>
     * 上传流程：
     * <ol>
     *   <li>校验文件类型和大小</li>
     *   <li>创建临时文件（staging 目录）</li>
     *   <li>流式写入文件，同时计算 MD5/SHA-256</li>
     *   <li>创建 Redis 会话（TTL 2h）</li>
     *   <li>返回会话 ID（用于后续提交）</li>
     * </ol>
     * </p>
     *
     * @param file      上传文件
     * @param productId 关联的产品 ID
     * @return 上传会话（包含文件元数据：MD5、SHA-256、文件大小等）
     * @throws com.wewins.fota.common.exception.BizException 上传失败
     */
    FirmwareUploadSession uploadToFirmwareStaging(MultipartFile file, Long productId);

    /**
     * 查询上传会话。
     * <p>
     * 用于前端轮询上传状态或恢复会话。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 上传会话
     * @throws com.wewins.fota.common.exception.BizException 会话不存在或已过期
     */
    FirmwareUploadSession getUploadSession(String sessionId);

    /**
     * 消费上传会话（一次性读取并删除）。
     * <p>
     * 用于提交固件版本时获取上传的文件信息。
     * </p>
     * <p>
     * 消费流程：
     * <ol>
     *   <li>查询会话（不存在则抛异常）</li>
     *   <li>删除 Redis 会话（幂等）</li>
     *   <li>返回会话数据</li>
     * </ol>
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 上传会话
     * @throws com.wewins.fota.common.exception.BizException 会话不存在或已过期
     */
    FirmwareUploadSession consumeUploadSession(String sessionId);

    /**
     * 取消上传会话并清理临时文件。
     * <p>
     * 用于用户主动取消上传或清理超时会话。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 是否清理成功（false = 会话不存在）
     */
    boolean cancelUploadSession(String sessionId);

    /**
     * 保存或刷新上传会话。
     * <p>
     * 用于转存后回写 objectKey，保证后续重试可复用会话。
     * </p>
     *
     * @param session 上传会话
     */
    void saveUploadSession(FirmwareUploadSession session);

    /**
     * 删除上传会话（不清理临时文件）。
     * <p>
     * 用于业务成功后清理会话，避免会话提前删除导致失败不可重试。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 是否删除成功（false = 会话不存在）
     */
    boolean deleteUploadSession(String sessionId);
}
