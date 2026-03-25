package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("device_import_batches")
public class DeviceImportBatchPO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String batchName;

    private Long productId;

    private String status;

    private String sourceFile;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /**
     * 逻辑删除标记 (0=未删除, 1=已删除)
     */
    @TableLogic
    private Integer deleted;
}
