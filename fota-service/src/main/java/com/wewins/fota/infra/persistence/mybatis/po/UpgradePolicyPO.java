package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.entity.BaseEntity;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.model.enums.TriggerMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("upgrade_policies")
public class UpgradePolicyPO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;

    private String name;

    private String remark;

    private Long targetVersionId;

    private String sourceVersions;

    private Integer priority;

    private Integer grayRate;

    private PolicyStatus status;

    private TriggerMode triggerMode;

    private String targetMode;

    private String targetImeis;

    private String targetDeviceBatchIds;

    private String targetDeviceTags;

    private String timeWindow;

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
