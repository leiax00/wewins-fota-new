package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("upgrade_policy_target_devices")
public class UpgradePolicyTargetDevicePO implements Serializable {

    private Long policyId;

    private String imei;

    private LocalDateTime createdAt;
}
