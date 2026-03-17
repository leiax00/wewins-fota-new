package com.wewins.fota.module.system.domain.entity.rbac;

import com.baomidou.mybatisplus.annotation.*;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色实体
 * <p>
 * 存储系统角色信息，用于 RBAC 权限控制
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_roles")
public class Role extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色编码（唯一）
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 角色状态
     */
    private String status;

    /**
     * 软删除时间
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
