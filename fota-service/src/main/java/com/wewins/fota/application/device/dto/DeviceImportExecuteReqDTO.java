package com.wewins.fota.application.device.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 执行设备导入请求 DTO
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Data
public class DeviceImportExecuteReqDTO {

    /**
     * 产品ID
     */
    @NotNull(message = "产品ID不能为空")
    @Positive(message = "产品ID必须为正数")
    private Long productId;

    /**
     * 批次名称（可选）
     */
    private String batchName;

    /**
     * 会话ID（与 imeis 二选一）
     * <p>
     * 当通过文件上传预估时，使用 sessionId 关联
     * </p>
     */
    private String sessionId;

    /**
     * IMEI 列表（与 sessionId 二选一）
     * <p>
     * 当通过文本输入时，直接传递 IMEI 列表
     * </p>
     */
    @NotEmpty(message = "IMEI列表不能为空")
    private List<String> imeis;

    /**
     * 数据来源文件名（可选）
     * <p>
     * 用于记录数据来源，如文件名或 "文本输入"
     * </p>
     */
    private String sourceFile;
}
