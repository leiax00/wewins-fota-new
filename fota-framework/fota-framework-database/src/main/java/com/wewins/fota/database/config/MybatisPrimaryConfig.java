package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.wewins.fota.database.annotation.PrimaryDbMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis 主数据源配置
 * <p>
 * 负责配置主数据库的 MyBatis-Plus 会话工厂、Mapper 扫描和事务管理器
 * 从 application.yml 读取 MyBatis-Plus 配置并应用
 * </p>
 * <p>
 * 支持多数据库：PostgreSQL、MySQL（自动识别数据库类型）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Configuration
@MapperScan(
        basePackages = "com.wewins.fota",
        annotationClass = PrimaryDbMapper.class,
        sqlSessionFactoryRef = "primarySqlSessionFactory"
)
@Slf4j
public class MybatisPrimaryConfig {

    /**
     * 分页插件（自动识别数据库类型）
     * <p>
     * 不指定 DbType，MyBatis-Plus 会通过 JDBC DatabaseMetaData 自动识别
     * </p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 不指定 DbType，自动识别数据库类型
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

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
            MybatisPlusProperties properties,
            ObjectProvider<MetaObjectHandler> metaObjectHandlerProvider
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
        GlobalConfig globalConfig = properties.getGlobalConfig();
        if (globalConfig == null) {
            globalConfig = new GlobalConfig();
        }

        MetaObjectHandler metaObjectHandler = metaObjectHandlerProvider.getIfAvailable();
        if (metaObjectHandler != null) {
            globalConfig.setMetaObjectHandler(metaObjectHandler);
            log.info("MyBatis-Plus 自动填充已启用: metaObjectHandler={}",
                    metaObjectHandler.getClass().getName());
        } else {
            log.warn("MyBatis-Plus 自动填充未启用: 未找到 MetaObjectHandler Bean");
        }
        factory.setGlobalConfig(globalConfig);
        factory.setPlugins(mybatisPlusInterceptor());

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
