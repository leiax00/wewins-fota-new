package com.wewins.fota.module.system.domain.entity.dict;

import com.baomidou.mybatisplus.annotation.*;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型实体
 * <p>
 * 存储系统字典类型信息，如设备状态、升级状态等
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dict_type")
public class DictType extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 字典类型编码（唯一）
     */
    private String code;

    /**
     * 字典类型名称
     */
    private String name;

    /**
     * 国际化key前缀
     */
    private String i18nKey;

    /**
     * 字典类型状态
     */
    private String status;

    /**
     * 字典类型描述
     */
    private String description;

    /**
     * 软删除时间
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
