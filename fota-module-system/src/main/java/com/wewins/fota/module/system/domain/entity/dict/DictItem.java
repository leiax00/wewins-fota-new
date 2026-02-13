package com.wewins.fota.module.system.domain.entity.dict;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.database.handler.JsonNodeTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典项实体
 * <p>
 * 存储字典类型下的具体字典项，如设备状态下的"在线"、"离线"等
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dict_item")
public class DictItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 字典项ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 字典类型ID
     */
    private Long dictTypeId;

    /**
     * 字典项标签
     */
    private String label;

    /**
     * 字典项值
     */
    private String value;

    /**
     * 国际化key
     */
    private String i18nKey;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 字典项状态
     */
    private String status;

    /**
     * 扩展信息（JSON）
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode extra;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 创建人用户ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 更新人用户ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 软删除时间
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
