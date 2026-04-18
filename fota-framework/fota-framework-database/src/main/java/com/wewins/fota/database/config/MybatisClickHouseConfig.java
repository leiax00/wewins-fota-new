package com.wewins.fota.database.config;

import com.wewins.fota.database.annotation.ClickHouseMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis ClickHouse 数据源配置（分析库）
 * <p>
 * 负责配置 ClickHouse 分析数据库的 MyBatis 会话工厂、Mapper 扫描和事务管理器
 * 仅当配置了 spring.datasource.clickhouse.url 时才会生效
 * </p>
 * <p>
 * 注意：ClickHouse 主要用于分析查询，不需要 MyBatis-Plus 的增强功能（如逻辑删除）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.clickhouse.url")
@MapperScan(
        basePackages = "com.wewins.fota",
        annotationClass = ClickHouseMapper.class,
        sqlSessionFactoryRef = "clickhouseSqlSessionFactory"
)
public class MybatisClickHouseConfig {

    /**
     * 配置 ClickHouse 数据源的 SqlSessionFactory
     *
     * @param dataSource ClickHouse 数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean
    public SqlSessionFactory clickhouseSqlSessionFactory(
            @Qualifier("clickhouseDataSource") DataSource dataSource
    ) throws Exception {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);

        org.mybatis.spring.SqlSessionFactoryBean factory = new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] infraResources = resolver.getResources("classpath*:mapper/infra/reporting/**/*.xml");
        Resource[] clickhouseResources = resolver.getResources("classpath*:mapper/clickhouse/reporting/**/*.xml");
        List<Resource> mapperResources = new ArrayList<>(infraResources.length + clickhouseResources.length);
        mapperResources.addAll(List.of(infraResources));
        mapperResources.addAll(List.of(clickhouseResources));
        factory.setMapperLocations(mapperResources.toArray(Resource[]::new));
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
