package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis 主数据源配置（PostgreSQL）
 * <p>
 * 负责配置主数据库的 MyBatis 会话工厂、Mapper 扫描和事务管理器
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Configuration
@MapperScan(
        basePackages = {
                "com.wewins.fota.mapper",
                "com.wewins.fota.system.mapper"
        },
        sqlSessionFactoryRef = "primarySqlSessionFactory",
        sqlSessionTemplateRef = "primarySqlSessionTemplate"
)
public class MybatisPrimaryConfig {

    /**
     * 配置主数据源的 SqlSessionFactory
     * <p>
     * 使用 MybatisPlus 的工厂 Bean，支持 MyBatis-Plus 的增强功能
     * </p>
     *
     * @param dataSource 主数据源（PostgreSQL）
     * @param properties MyBatis-Plus 配置属性
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource dataSource,
            MybatisPlusProperties properties
    ) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(properties.resolveMapperLocations());
        factory.setTypeAliasesPackage(properties.getTypeAliasesPackage());
        factory.setTypeHandlersPackage(properties.getTypeHandlersPackage());
        factory.setConfiguration(properties.getConfiguration());
        factory.setGlobalConfig(properties.getGlobalConfig());
        return factory.getObject();
    }

    /**
     * 配置主数据源的 SqlSessionTemplate
     *
     * @param sqlSessionFactory 主数据源的 SqlSessionFactory
     * @return SqlSessionTemplate
     */
    @Bean
    @Primary
    public SqlSessionTemplate primarySqlSessionTemplate(
            @Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 配置主数据源的事务管理器
     * <p>
     * 用于管理主数据库的事务边界，支持 @Transactional 注解
     * </p>
     *
     * @param dataSource 主数据源（PostgreSQL）
     * @return DataSourceTransactionManager
     */
    @Bean
    @Primary
    public DataSourceTransactionManager primaryTransactionManager(
            @Qualifier("primaryDataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }
}
