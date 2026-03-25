package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("upgrade_policy_source_versions")
public class UpgradePolicySourceVersionPO implements Serializable {

    private Long policyId;

    private Long sourceVersionId;

    private LocalDateTime createdAt;
}
