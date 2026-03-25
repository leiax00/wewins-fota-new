package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("upgrade_policy_target_batches")
public class UpgradePolicyTargetBatchPO implements Serializable {

    private Long policyId;

    private Long batchId;

    private LocalDateTime createdAt;
}
