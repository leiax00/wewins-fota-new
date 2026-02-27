package com.wewins.fota.application.device.dto;

import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import lombok.Data;

import java.util.List;

/**
 * 批量操作请求DTO
 * <p>
 * 支持多种批量操作类型，不同类型使用不同的参数组合
 * </p>
 */
@Data
public class BatchOperationReqDTO {

    /**
     * 操作类型（必填）
     */
    private BatchOperationType operationType;

    // ==================== 按批次操作参数 ====================
    /**
     * 批次ID
     * <p>
     * 用于：按批次删除、按批次修改标签
     * </p>
     */
    private Long batchId;

    // ==================== 按IMEI列表操作参数 ====================
    /**
     * IMEI列表
     * <p>
     * 用于：按IMEI列表修改标签、按IMEI列表修改批次
     * </p>
     */
    private List<String> imeis;

    // ==================== 按查询条件操作参数 ====================
    /**
     * 产品ID
     * <p>
     * 用于：按查询条件操作（可选）
     * </p>
     */
    private Long productId;

    /**
     * IMEI关键词（模糊匹配）
     * <p>
     * 用于：按查询条件操作（可选）
     * </p>
     */
    private String imeiKeyword;

    /**
     * 设备状态
     * <p>
     * 用于：按查询条件操作（可选）
     * </p>
     */
    private String status;

    /**
     * 导入批次ID
     * <p>
     * 用于：按查询条件操作（可选）
     * </p>
     */
    private Long importBatchId;

    // ==================== 操作参数 ====================
    /**
     * 新标签（JSON字符串）
     * <p>
     * 用于：修改标签操作
     * </p>
     */
    private String tags;

    /**
     * 新批次ID
     * <p>
     * 用于：修改批次操作
     * </p>
     */
    private Long newBatchId;

    /**
     * 验证请求参数是否合法
     */
    public void validate() {
        if (operationType == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "操作类型不能为空");
        }

        switch (operationType) {
            case DELETE_BY_BATCH, UPDATE_TAG_BY_BATCH -> {
                if (batchId == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "批次ID不能为空");
                }
            }
            case UPDATE_TAG_BY_IMEI, UPDATE_BATCH_BY_IMEI -> {
                if (imeis == null || imeis.isEmpty()) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "IMEI列表不能为空");
                }
            }
            case UPDATE_TAG_BY_QUERY, UPDATE_BATCH_BY_QUERY -> {
                // 查询条件至少要有一个
                if (productId == null && (imeiKeyword == null || imeiKeyword.isBlank())
                        && (status == null || status.isBlank()) && importBatchId == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "查询条件不能为空，请至少选择一个筛选条件");
                }
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的操作类型: " + operationType);
        }

        // 验证操作参数
        if (operationType == BatchOperationType.UPDATE_TAG_BY_BATCH
                || operationType == BatchOperationType.UPDATE_TAG_BY_IMEI
                || operationType == BatchOperationType.UPDATE_TAG_BY_QUERY) {
            if (tags == null || tags.isBlank()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "标签内容不能为空");
            }
        }

        if (operationType == BatchOperationType.UPDATE_BATCH_BY_IMEI
                || operationType == BatchOperationType.UPDATE_BATCH_BY_QUERY) {
            if (newBatchId == null) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "新批次ID不能为空");
            }
        }
    }
}
