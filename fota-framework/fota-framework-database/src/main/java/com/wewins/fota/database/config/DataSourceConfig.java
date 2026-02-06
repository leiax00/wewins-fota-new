package com.wewins.fota.database.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置类
 * <p>
 * 配置 PostgreSQL 主数据源和 ClickHouse 分析数据源
 * 所有连接信息从 application.yml 读取，支持环境变量覆盖
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    /**
     * PostgreSQL 数据源属性配置
     * <p>
     * 从 application.yml 读取基本配置：
     * - spring.datasource.url
     * - spring.datasource.username
     * - spring.datasource.password
     * - spring.datasource.driver-class-name
     * </p>
     *
     * @return DataSourceProperties
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 配置 PostgreSQL 主数据源
     * <p>
     * 使用 Spring Boot 自动配置，从 application.yml 读取：
     * - spring.datasource.url
     * - spring.datasource.username
     * - spring.datasource.password
     * - spring.datasource.hikari.*
     * </p>
     *
     * @param primaryDataSourceProperties 数据源属性
     * @return PostgreSQL 数据源
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource primaryDataSource(DataSourceProperties primaryDataSourceProperties) {
        log.info("[DataSource] 初始化 PostgreSQL 主数据源");

        HikariDataSource dataSource = primaryDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        log.info("[DataSource] PostgreSQL 主数据源初始化完成: {}", dataSource.getJdbcUrl());
        return dataSource;
    }

    /**
     * ClickHouse 数据源属性配置
     * <p>
     * 从 application.yml 读取基本配置：
     * - spring.datasource.clickhouse.url
     * - spring.datasource.clickhouse.username
     * - spring.datasource.clickhouse.password
     * - spring.datasource.clickhouse.driver-class-name
     * </p>
     *
     * @return DataSourceProperties
     */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
    @ConfigurationProperties(prefix = "spring.datasource.clickhouse")
    public DataSourceProperties clickhouseDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 配置 ClickHouse 分析数据源（可选）
     * <p>
     * 仅当配置了 spring.datasource.clickhouse.url 时才创建
     * 从 application.yml 读取：
     * - spring.datasource.clickhouse.url
     * - spring.datasource.clickhouse.username
     * - spring.datasource.clickhouse.password
     * - spring.datasource.clickhouse.hikari.*
     * </p>
     *
     * @param clickhouseDataSourceProperties ClickHouse 数据源属性
     * @return ClickHouse 数据源
     */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
    @ConfigurationProperties(prefix = "spring.datasource.clickhouse.hikari")
    public DataSource clickhouseDataSource(DataSourceProperties clickhouseDataSourceProperties) {
        log.info("[DataSource] 初始化 ClickHouse 分析数据源");

        HikariDataSource dataSource = clickhouseDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        log.info("[DataSource] ClickHouse 分析数据源初始化完成: {}", dataSource.getJdbcUrl());
        return dataSource;
    }
}
