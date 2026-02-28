package com.wewins.fota.application.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量操作结果DTO
 * <p>
 * 返回批量操作的执行统计和错误信息
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResultDTO {

    /**
     * 总操作数
     */
    private Integer totalCount;

    /**
     * 成功数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failedCount;

    /**
     * 错误信息列表
     */
    private List<String> errors;
}
