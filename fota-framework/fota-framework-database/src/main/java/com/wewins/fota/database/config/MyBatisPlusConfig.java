package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.MybatisMapWrapperFactory;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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
 * - 驼峰命名转换
 * - 自动填充
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Configuration
@MapperScan("com.wewins.fota.mapper")
public class MyBatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 拦截器
     * <p>
     * 基础配置，可后续添加分页插件等
     * </p>
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        System.out.println("[MyBatis-Plus] 初始化 MyBatis-Plus 拦截器");

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 可在此处添加分页插件、乐观锁插件等
        // PaginationInnerInterceptor 将在后续添加

        System.out.println("[MyBatis-Plus] MyBatis-Plus 拦截器初始化完成");
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
        System.out.println("[MyBatis-Plus] 初始化 SqlSessionFactory");

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
        factoryBean.setTypeAliasesPackage("com.wewins.fota.entity");

        // 添加 MyBatis-Plus 插件
        factoryBean.setPlugins(mybatisPlusInterceptor());

        System.out.println("[MyBatis-Plus] SqlSessionFactory 初始化完成");
        return factoryBean.getObject();
    }
}
