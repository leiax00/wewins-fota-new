package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("upgrade_policy_target_tags")
public class UpgradePolicyTargetTagPO implements Serializable {

    private Long id;

    private Long policyId;

    private String tagKey;

    private String tagValue;

    private String operator;

    private LocalDateTime createdAt;
}
