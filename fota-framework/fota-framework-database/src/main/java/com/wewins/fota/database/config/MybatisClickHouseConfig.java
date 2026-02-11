package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis ClickHouse 数据源配置（分析库）
 * <p>
 * 负责配置 ClickHouse 分析数据库的 MyBatis 会话工厂、Mapper 扫描和事务管理器
 * 仅当配置了 spring.datasource.clickhouse.url 时才会生效
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
@MapperScan(
        basePackages = "com.wewins.fota.clickhouse.mapper",
        sqlSessionFactoryRef = "clickhouseSqlSessionFactory",
        sqlSessionTemplateRef = "clickhouseSqlSessionTemplate"
)
public class MybatisClickHouseConfig {

    /**
     * 配置 ClickHouse 数据源的 SqlSessionFactory
     * <p>
     * 使用独立的 Mapper 路径：classpath*:mapper/clickhouse/**/*.xml
     * </p>
     *
     * @param dataSource ClickHouse 数据源
     * @param properties MyBatis-Plus 配置属性（共享类型别名和处理器配置）
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean
    public SqlSessionFactory clickhouseSqlSessionFactory(
            @Qualifier("clickhouseDataSource") DataSource dataSource,
            MybatisPlusProperties properties
    ) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/clickhouse/**/*.xml")
        );
        factory.setTypeAliasesPackage(properties.getTypeAliasesPackage());
        factory.setTypeHandlersPackage(properties.getTypeHandlersPackage());
        factory.setConfiguration(properties.getConfiguration());
        factory.setGlobalConfig(properties.getGlobalConfig());
        return factory.getObject();
    }

    /**
     * 配置 ClickHouse 数据源的 SqlSessionTemplate
     *
     * @param sqlSessionFactory ClickHouse 数据源的 SqlSessionFactory
     * @return SqlSessionTemplate
     */
    @Bean
    public SqlSessionTemplate clickhouseSqlSessionTemplate(
            @Qualifier("clickhouseSqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 配置 ClickHouse 数据源的事务管理器
     * <p>
     * 用于管理 ClickHouse 数据库的事务边界
     * 注意：ClickHouse 的事务支持有限，主要用于批量操作一致性
     * </p>
     *
     * @param dataSource ClickHouse 数据源
     * @return DataSourceTransactionManager
     */
    @Bean
    public DataSourceTransactionManager clickhouseTransactionManager(
            @Qualifier("clickhouseDataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }
}
