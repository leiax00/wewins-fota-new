package com.wewins.fota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FOTA 平台启动类
 *
 * <p>支持两种运行模式：
 * <ul>
 *   <li>app.mode=main：主区域模式（管理后台、配置中心、数据汇聚）</li>
 *   <li>app.mode=region：区域模式（设备 API、配置同步、数据转发）</li>
 * </ul>
 *
 * <p>运行模式通过配置项 {@code app.mode}（可由环境变量 {@code APP_MODE} 覆盖）指定。
 *
 * <p>MyBatis Mapper 扫描配置已拆分到：
 * <ul>
 *   <li>{@link com.wewins.fota.database.config.MybatisPrimaryConfig} - 主数据源（PostgreSQL）</li>
 *   <li>{@link com.wewins.fota.database.config.MybatisClickHouseConfig} - 分析数据源（ClickHouse）</li>
 * </ul>
 *
 * @see <a href="https://github.com/wewins/wewins-fota-new">项目文档</a>
 * @since 0.1.0
 */
@SpringBootApplication
public class FotaApplication {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FotaApplication.class);
        app.run(args);
    }
}
