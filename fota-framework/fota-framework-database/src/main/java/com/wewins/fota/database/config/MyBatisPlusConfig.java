package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.MybatisMapWrapperFactory;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidouplusplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 配置 MyBatis-Plus 的核心功能：
 * - Mapper 扫描
 * - 分页插件
 * - 驼峰命名转换
 * - 自动填充
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Slf4j
@Configuration
@MapperScan("com.wewins.fota.database.mapper")
public class MyBatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 拦截器
     * <p>
     * 添加分页插件和其他插件
     * </p>
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        log.info("初始化 MyBatis-Plus 拦截器");

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(1000L); // 最大单页限制数量
        paginationInterceptor.setOverflow(false); // 溢出总页数后是否进行处理

        interceptor.addInnerInterceptor(paginationInterceptor);

        log.info("MyBatis-Plus 拦截器初始化完成");
        return interceptor;
    }

    /**
     * 配置 SqlSessionFactory
     * <p>
     * 设置 MyBatis-Plus 的核心配置
     * </p>
     *
     * @param dataSource 数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        log.info("初始化 SqlSessionFactory");

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

        // MyBatis-Plus 配置
        MybatisConfiguration configuration = new MybatisConfiguration();

        // 开启驼峰命名转换
        configuration.setMapUnderscoreToCamelCase(true);

        // 日志实现
        configuration.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);

        // 支持方法引用（Map <String, Object>）
        configuration.setObjectWrapperFactory(new MybatisMapWrapperFactory());

        factoryBean.setConfiguration(configuration);

        // Mapper XML 文件位置
        factoryBean.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mapper/**/*.xml")
        );

        // 类型别名包
        factoryBean.setTypeAliasesPackage("com.wewins.fota.database.entity");

        // 添加 MyBatis-Plus 插件
        factoryBean.setPlugins(mybatisPlusInterceptor());

        log.info("SqlSessionFactory 初始化完成");
        return factoryBean.getObject();
    }
}
