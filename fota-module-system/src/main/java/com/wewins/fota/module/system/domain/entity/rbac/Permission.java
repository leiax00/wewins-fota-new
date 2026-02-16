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
 * 系统权限实体
 * <p>
 * 存储系统权限信息，支持 API、菜单、按钮等权限类型
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_permissions")
public class Permission extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限编码（唯一）
     */
    private String code;

    /**
     * 权限名称
     */
    private String name;

    /**
     * 权限类型（API/MENU/BUTTON）
     */
    private String type;

    /**
     * API 资源路径
     */
    private String path;

    /**
     * HTTP 方法（GET/POST/PUT/DELETE）
     */
    private String method;

    /**
     * 父权限ID（用于构建权限树）
     */
    private Long parentId;

    /**
     * 权限状态
     */
    private String status;

    /**
     * 软删除时间
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
