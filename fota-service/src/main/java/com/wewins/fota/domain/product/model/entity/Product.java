package com.wewins.fota.domain.product.model.entity;

import com.wewins.fota.common.util.TimeConstants;
import com.wewins.fota.domain.base.entity.DomainEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 产品实体类
 * <p>
 * 对应数据库表：products
 * 存储产品的基本信息，每个产品代表一类设备
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Product extends DomainEntity {

    private static final int DEFAULT_CHECK_PERIOD_SECONDS = 6 * TimeConstants.SECONDS_PER_HOUR;

    /**
     * 产品名称
     */
    private String name;

    /**
     * 制造商
     */
    private String manufacturer;

    /**
     * 产品型号
     */
    private String model;

    /**
     * 产品备注
     */
    private String remark;

    /**
     * 产品默认检测周期，单位秒。
     * <p>
     * 作为该产品的基础检测周期，再叠加运行时负载调节与保护逻辑。
     * 默认 6 小时。
     * </p>
     */
    @Builder.Default
    private Integer checkPeriodSeconds = DEFAULT_CHECK_PERIOD_SECONDS;

    /**
     * 软删除时间（逻辑删除）
     */
    private LocalDateTime deletedAt;
}
