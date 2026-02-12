package com.wewins.fota.module.system.dto;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.database.wrapper.LambdaQueryWrapperX;
import com.wewins.fota.module.system.entity.DictItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 字典项分页查询 ReqVO
 * <p>
 * 提供字典项列表查询的专用请求参数，支持状态过滤、时间范围查询和排序
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * GET /api/sys/dict-items?dictTypeId=1&status=active&page=1&size=20
 * }</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictItemPageReqVO extends BaseRequestVo {

    /**
     * 字典类型ID
     */
    private Long dictTypeId;

    /**
     * 字典项标签（模糊查询）
     */
    private String label;

    /**
     * 字典项值（模糊查询）
     */
    private String value;

    /**
     * 国际化 key（模糊查询）
     */
    private String i18nKey;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 字典项状态
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
    private static final Map<String, SFunction<DictItem, ?>> FIELD_MAP = new HashMap<>();
    static {
        FIELD_MAP.put("dictTypeId", DictItem::getDictTypeId);
        FIELD_MAP.put("label", DictItem::getLabel);
        FIELD_MAP.put("value", DictItem::getValue);
        FIELD_MAP.put("i18nKey", DictItem::getI18nKey);
        FIELD_MAP.put("sortOrder", DictItem::getSortOrder);
        FIELD_MAP.put("status", DictItem::getStatus);
        FIELD_MAP.put("createdAt", DictItem::getCreatedAt);
        FIELD_MAP.put("updatedAt", DictItem::getUpdatedAt);
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
    public LambdaQueryWrapperX<DictItem> toWrapper() {
        LambdaQueryWrapperX<DictItem> wrapper = new LambdaQueryWrapperX<>(DictItem.class)
                .eqIfPresent(DictItem::getDictTypeId, getDictTypeId())
                .eqIfPresent(DictItem::getSortOrder, getSortOrder())
                .eqIfPresent(DictItem::getStatus, getStatus())
                .likeIfPresent(DictItem::getLabel, getLabel())
                .likeIfPresent(DictItem::getValue, getValue())
                .likeIfPresent(DictItem::getI18nKey, getI18nKey())
                .betweenIfPresent(DictItem::getCreatedAt, getCreateTimeStart(), getCreateTimeEnd())
                .betweenIfPresent(DictItem::getUpdatedAt, getUpdateTimeStart(), getUpdateTimeEnd());

        wrapper.applySortingIfPresent(getSortingFields(), FIELD_MAP);
        wrapper.applyFiltersIfPresent(getFilters(), FIELD_MAP);
        return wrapper;
    }
}
