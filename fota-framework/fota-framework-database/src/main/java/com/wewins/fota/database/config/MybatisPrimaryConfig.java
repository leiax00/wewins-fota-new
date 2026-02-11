package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis 主数据源配置（PostgreSQL）
 * <p>
 * 负责配置主数据库的 MyBatis-Plus 会话工厂、Mapper 扫描和事务管理器
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
     * 配置主数据源的 SqlSessionFactory（MyBatis-Plus）
     *
     * @param dataSource 主数据源（PostgreSQL）
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource dataSource
    ) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        // 配置驼峰映射
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);

        // 配置 MyBatis-Plus 全局配置（逻辑删除等）
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        // 逻辑删除配置：使用 deletedAt 字段
        dbConfig.setLogicDeleteField("deletedAt");
        dbConfig.setLogicNotDeleteValue("NULL");
        dbConfig.setLogicDeleteValue("now()");
        globalConfig.setDbConfig(dbConfig);
        factory.setGlobalConfig(globalConfig);

        // 配置 Mapper 位置（排除 ClickHouse）
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/postgresql/**/*.xml")
        );

        // 配置类型别名包
        factory.setTypeAliasesPackage("com.wewins.fota.entity,com.wewins.fota.system.entity");

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
