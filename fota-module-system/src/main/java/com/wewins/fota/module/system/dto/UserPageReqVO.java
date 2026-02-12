package com.wewins.fota.module.system.dto;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wewins.fota.common.dto.BaseReqVO;
import com.wewins.fota.database.wrapper.LambdaQueryWrapperX;
import com.wewins.fota.module.system.entity.User;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户分页查询 ReqVO
 * <p>
 * 提供用户列表查询的专用请求参数，支持状态过滤、时间范围查询和排序
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * GET /api/sys/users?status=active&page=1&size=20
 * }</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageReqVO extends BaseReqVO {

    private String username;

    private String phone;

    private String email;

    /**
     * 用户状态
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
     * <p>
     * 传参格式（二选一）：
     * <ul>
     *   <li>重复参数：createTime=2025-01-01T00:00:00&createTime=2025-12-31T23:59:59</li>
     *   <li>逗号分隔：createTime=2025-01-01T00:00:00,2025-12-31T23:59:59</li>
     * </ul>
     * </p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime[] createTime;

    /**
     * 更新时间范围
     * <p>
     * 数组格式：[起始时间, 结束时间]
     * </p>
     * <p>
     * 传参格式（二选一）：
     * <ul>
     *   <li>重复参数：updateTime=2025-01-01T00:00:00&updateTime=2025-12-31T23:59:59</li>
     *   <li>逗号分隔：updateTime=2025-01-01T00:00:00,2025-12-31T23:59:59</li>
     * </ul>
     * </p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime[] updateTime;

    /**
     * 字段映射（用于排序与 filters）
     */
    private static final Map<String, SFunction<User, ?>> FIELD_MAP = new HashMap<>();
    static {
        FIELD_MAP.put("username", User::getUsername);
        FIELD_MAP.put("displayName", User::getDisplayName);
        FIELD_MAP.put("status", User::getStatus);
        FIELD_MAP.put("createdAt", User::getCreatedAt);
        FIELD_MAP.put("updatedAt", User::getUpdatedAt);
        FIELD_MAP.put("phone", User::getPhone);
        FIELD_MAP.put("email", User::getEmail);
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
    public LambdaQueryWrapperX<User> toWrapper() {
        LambdaQueryWrapperX<User> wrapper = new LambdaQueryWrapperX<>(User.class)
                .eqIfPresent(User::getStatus, getStatus())
                .likeIfPresent(User::getUsername, getUsername())
                .likeIfPresent(User::getEmail, getEmail())
                .likeIfPresent(User::getPhone, getPhone())
                .betweenIfPresent(User::getCreatedAt, getCreateTimeStart(), getCreateTimeEnd())
                .betweenIfPresent(User::getUpdatedAt, getUpdateTimeStart(), getUpdateTimeEnd());

        wrapper.applySortingIfPresent(getSortingFields(), FIELD_MAP);
        wrapper.applyFiltersIfPresent(getFilters(), FIELD_MAP);
        return wrapper;
    }
}
