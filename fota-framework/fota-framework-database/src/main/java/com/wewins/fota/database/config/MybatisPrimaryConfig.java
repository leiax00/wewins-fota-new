package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
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
 * 负责配置主数据库的 MyBatis-Plus 会话工厂、Mapper 扫描和事务管理器
 * 从 application.yml 读取 MyBatis-Plus 配置并应用
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
     * <p>
     * 从 application.yml 读取并应用 MyBatis-Plus 配置，包括：
     * <ul>
     *   <li>驼峰映射：map-underscore-to-camel-case</li>
     *   <li>日志实现：log-impl</li>
     *   <li>逻辑删除：logic-delete-*</li>
     *   <li>类型别名：type-aliases-package</li>
     *   <li>类型处理器：type-handlers-package</li>
     * </ul>
     * </p>
     *
     * @param dataSource 主数据源（PostgreSQL）
     * @param properties MyBatis-Plus 配置属性（从 application.yml 自动加载）
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

        // 应用 application.yml 的 MyBatis-Plus 配置
        // 注意：properties.getConfiguration() 返回 CoreConfiguration（类型不兼容）
        // 使用 configurationProperties 让 MyBatis-Plus 自动映射配置项
        MybatisConfiguration configuration = new MybatisConfiguration();
        factory.setConfiguration(configuration);
        factory.setConfigurationProperties(properties.getConfigurationProperties());

        // 应用 Mapper 位置（从 application.yml 读取）
        factory.setMapperLocations(properties.resolveMapperLocations());

        // 应用类型别名包（从 application.yml 读取）
        factory.setTypeAliasesPackage(properties.getTypeAliasesPackage());

        // 应用类型处理器包（从 application.yml 读取）
        factory.setTypeHandlersPackage(properties.getTypeHandlersPackage());

        // 应用全局配置（逻辑删除等，从 application.yml 读取）
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
