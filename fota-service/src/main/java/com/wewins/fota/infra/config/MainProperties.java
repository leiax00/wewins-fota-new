package com.wewins.fota.infra.config;

import lombok.Data;

import java.util.List;

/**
 * 主区域配置属性
 * <p>
 * 当 app.mode=main 时使用的配置
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
public class MainProperties {

    /**
     * 主区域 API 地址
     */
    private String apiBaseUrl;

    /**
     * 子区域注册表
     * <p>
     * 主区域维护的所有子区域信息
     * </p>
     */
    private List<RegionRegistryEntry> regions;

    /**
     * 子区域注册条目
     */
    @Data
    public static class RegionRegistryEntry {
        /**
         * 区域唯一标识符
         */
        private String code;

        /**
         * 区域名称
         */
        private String name;

        /**
         * 区域 API 地址
         */
        private String apiBaseUrl;

        /**
         * 区域时区
         */
        private String timeZone;

        /**
         * 区域状态
         */
        private String status;
    }
}
