package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.handler.JsonbStringTypeHandler;
import com.wewins.fota.database.entity.BaseEntity;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.model.enums.TriggerMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "upgrade_policies", autoResultMap = true)
public class UpgradePolicyPO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;

    private String name;

    private String remark;

    private Long targetVersionId;

    private Long affectedTotal;

    private Integer priority;

    private Integer grayRate;

    private PolicyStatus status;

    private TriggerMode triggerMode;

    private String targetMode;

    @TableField(typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String timeWindow;

    /**
     * 逻辑删除标记 (0=未删除, 1=已删除)
     */
    @TableLogic
    private Integer deleted;
}
