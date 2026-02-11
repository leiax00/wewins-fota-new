package com.wewins.fota.system.dto;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.database.wrapper.LambdaQueryWrapperX;
import com.wewins.fota.system.entity.Permission;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 权限分页查询 ReqVO
 * <p>
 * 提供权限列表查询的专用请求参数，支持类型、状态过滤与时间范围查询
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * GET /api/sys/permissions?type=API&status=active&page=1&size=20
 * }</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionPageReqVO extends BaseRequestVo {

    /**
     * 权限编码（模糊查询）
     */
    private String code;

    /**
     * 权限名称（模糊查询）
     */
    private String name;

    /**
     * 权限类型
     * <p>
     * 可选值：API（接口权限）、MENU（菜单权限）、BUTTON（按钮权限）
     * </p>
     */
    private String type;

    /**
     * API 资源路径（模糊查询）
     */
    private String path;

    /**
     * HTTP 方法
     * <p>
     * 可选值：GET、POST、PUT、DELETE、PATCH
     * </p>
     */
    private String method;

    /**
     * 父权限ID
     */
    private Long parentId;

    /**
     * 权限状态
     * <p>
     * 可选值：active（启用）、disabled（禁用）
     * </p>
     */
    private String status;

    /**
     * 创建时间范围
     * <p>
     * 数组格式：[起始时间, 结束时间]
     * </p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime[] createTime;

    /**
     * 更新时间范围
     * <p>
     * 数组格式：[起始时间, 结束时间]
     * </p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime[] updateTime;

    /**
     * 字段映射（用于排序与 filters）
     */
    private static final Map<String, SFunction<Permission, ?>> FIELD_MAP = new HashMap<>();
    static {
        FIELD_MAP.put("code", Permission::getCode);
        FIELD_MAP.put("name", Permission::getName);
        FIELD_MAP.put("type", Permission::getType);
        FIELD_MAP.put("path", Permission::getPath);
        FIELD_MAP.put("method", Permission::getMethod);
        FIELD_MAP.put("parentId", Permission::getParentId);
        FIELD_MAP.put("status", Permission::getStatus);
        FIELD_MAP.put("createdAt", Permission::getCreatedAt);
        FIELD_MAP.put("updatedAt", Permission::getUpdatedAt);
    }

    /**
     * 获取创建时间起始值
     *
     * @return 起始时间，可能为 null
     */
    public LocalDateTime getCreateTimeStart() {
        LocalDateTime[] createTime = this.createTime;
        return (createTime != null && createTime.length > 0) ? createTime[0] : null;
    }

    /**
     * 获取创建时间结束值
     *
     * @return 结束时间，可能为 null
     */
    public LocalDateTime getCreateTimeEnd() {
        LocalDateTime[] createTime = this.createTime;
        return (createTime != null && createTime.length > 1) ? createTime[1] : null;
    }

    /**
     * 获取更新时间起始值
     *
     * @return 起始时间，可能为 null
     */
    public LocalDateTime getUpdateTimeStart() {
        LocalDateTime[] updateTime = this.updateTime;
        return (updateTime != null && updateTime.length > 0) ? updateTime[0] : null;
    }

    /**
     * 获取更新时间结束值
     *
     * @return 结束时间，可能为 null
     */
    public LocalDateTime getUpdateTimeEnd() {
        LocalDateTime[] updateTime = this.updateTime;
        return (updateTime != null && updateTime.length > 1) ? updateTime[1] : null;
    }

    /**
     * 构建查询 Wrapper（类型安全）
     * <p>
     * 将 ReqVO 的查询条件（包括 typed 字段和 filters）转换为 LambdaQueryWrapperX
     * </p>
     *
     * @return LambdaQueryWrapperX 实例
     */
    public LambdaQueryWrapperX<Permission> toWrapper() {
        LambdaQueryWrapperX<Permission> wrapper = new LambdaQueryWrapperX<>(Permission.class)
                .eqIfPresent(Permission::getType, getType())
                .eqIfPresent(Permission::getMethod, getMethod())
                .eqIfPresent(Permission::getParentId, getParentId())
                .eqIfPresent(Permission::getStatus, getStatus())
                .likeIfPresent(Permission::getCode, getCode())
                .likeIfPresent(Permission::getName, getName())
                .likeIfPresent(Permission::getPath, getPath())
                .betweenIfPresent(Permission::getCreatedAt, getCreateTimeStart(), getCreateTimeEnd())
                .betweenIfPresent(Permission::getUpdatedAt, getUpdateTimeStart(), getUpdateTimeEnd());

        wrapper.applySortingIfPresent(getSortingFields(), FIELD_MAP);
        wrapper.applyFiltersIfPresent(getFilters(), FIELD_MAP);
        return wrapper;
    }
}
