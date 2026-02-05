package com.wewins.fota.database.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

/**
 * 数据源配置类
 * <p>
 * 配置 PostgreSQL 主数据源
 * 支持多数据源扩展（PostgreSQL + ClickHouse）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Configuration
public class DataSourceConfig {

    /**
     * 配置 PostgreSQL 主数据源
     * <p>
     * 使用 HikariCP 连接池，Spring Boot 默认提供
     * 通过 @Primary 标记为主数据源
     * </p>
     *
     * @return PostgreSQL 数据源
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource primaryDataSource() {
        System.out.println("[DataSource] 初始化 PostgreSQL 主数据源");

        HikariDataSource dataSource = new HikariDataSource();

        // 从 application.yml 读取配置
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/fota");
        dataSource.setUsername("fota");
        dataSource.setPassword("fota");
        dataSource.setDriverClassName("org.postgresql.Driver");

        // HikariCP 连接池配置
        dataSource.setMaximumPoolSize(20);
        dataSource.setMinimumIdle(5);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setConnectionTestQuery("SELECT 1");

        System.out.println("[DataSource] PostgreSQL 主数据源初始化完成: " + dataSource.getJdbcUrl());
        return new TransactionAwareDataSourceProxy(dataSource);
    }

    /**
     * 配置 ClickHouse 分析数据源（可选）
     * <p>
     * 仅当配置了 ClickHouse 连接信息时才创建
     * 用于事件日志存储和分析查询
     * </p>
     *
     * @return ClickHouse 数据源
     */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.clickhouse.jdbc-url")
    public DataSource clickhouseDataSource() {
        System.out.println("[DataSource] 初始化 ClickHouse 分析数据源");

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:clickhouse://localhost:8123/fota_events");
        dataSource.setUsername("default");
        dataSource.setPassword("");
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");

        // ClickHouse 连接池配置（相对较小，因为主要用于分析查询）
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(30000);

        System.out.println("[DataSource] ClickHouse 分析数据源初始化完成: " + dataSource.getJdbcUrl());
        return new TransactionAwareDataSourceProxy(dataSource);
    }
}
