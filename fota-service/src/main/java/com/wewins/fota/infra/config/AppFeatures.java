package com.wewins.fota.infra.config;

import lombok.Data;

/**
 * 应用功能开关配置
 * <p>
 * 用于显式声明各模块能力开关，替代隐式的 mode 判断
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Data
public class AppFeatures {

    /**
     * 管理后台能力
     */
    private boolean admin = true;

    /**
     * 设备 API 能力
     */
    private boolean deviceApi = true;

    /**
     * 跨区域数据接入能力
     */
    private boolean dataIngest = false;

    /**
     * 数据转发能力
     */
    private boolean dataForward = false;
}
