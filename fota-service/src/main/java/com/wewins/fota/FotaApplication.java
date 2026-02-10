package com.wewins.fota;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FOTA 平台启动类
 *
 * <p>支持两种运行模式：
 * <ul>
 *   <li>MODE=main：主区域模式（管理后台、配置中心、数据汇聚）</li>
 *   <li>MODE=region：区域模式（设备 API、配置同步、数据转发）</li>
 * </ul>
 *
 * <p>运行模式通过环境变量 {@code MODE} 或 JVM 参数 {@code -Dspring.profiles.active} 指定。
 *
 * @see <a href="https://github.com/wewins/wewins-fota-new">项目文档</a>
 * @since 0.1.0
 */
@SpringBootApplication
@MapperScan("com.wewins.fota.**.mapper")
public class FotaApplication {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FotaApplication.class);
        // MODE 环境变量会自动映射到 spring.profiles.active（见 application.yml）
        app.run(args);
    }
}
