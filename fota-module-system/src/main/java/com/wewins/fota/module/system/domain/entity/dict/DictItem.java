package com.wewins.fota.module.system.domain.entity.dict;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.database.entity.BaseEntity;
import com.wewins.fota.database.handler.JsonNodeTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

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
@TableName(value = "sys_dict_item", autoResultMap = true)
public class DictItem extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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
    @TableField(typeHandler = JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode extra;

    /**
     * 软删除时间
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
