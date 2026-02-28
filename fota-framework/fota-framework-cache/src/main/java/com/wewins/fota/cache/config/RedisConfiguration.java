package com.wewins.fota.cache.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.wewins.fota.cache.dto.FirmwareUploadSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis 配置类
 * <p>
 * 配置 Redis 相关 Bean，包括：
 * <ul>
 *   <li>RedisTemplate：用于对象序列化（JSON）</li>
 *   <li>StringRedisTemplate：用于字符串操作（Bitmap、限流等）</li>
 *   <li>Jackson 序列化器：安全的 JSON 序列化配置</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Configuration
public class RedisConfiguration {

    /**
     * 配置 StringRedisTemplate
     * <p>
     * 主要用于：
     * <ul>
     *   <li>Bitmap 操作（SETBIT、GETBIT、BITCOUNT）</li>
     *   <li>计数器（INCR、DECR）</li>
     *   <li>限流（Lua 脚本）</li>
     *   <li>简单 KV 存储</li>
     * </ul>
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return StringRedisTemplate bean
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 配置固定窗口限流 Lua 脚本
     * <p>
     * 脚本语义：
     * <ul>
     *   <li>对 key 执行 INCR 原子递增</li>
     *   <li>首次创建时设置过期时间窗口</li>
     *   <li>返回当前计数值</li>
     * </ul>
     * </p>
     * <p>
     * 使用方式：
     * <pre>
     * Long currentCount = stringRedisTemplate.execute(
     *     fixedWindowRateLimitScript,
     *     Collections.singletonList("rate_limit:key"),
     *     "60"   // 时间窗口（秒）
     * );
     * boolean allowed = currentCount <= MAX_REQUESTS;
     * </pre>
     * </p>
     *
     * @return 固定窗口限流脚本 bean
     */
    @Bean
    public DefaultRedisScript<Long> fixedWindowRateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/ratelimit/fixed_window.lua")));
        return script;
    }

    /**
     * 配置 RedisTemplate
     * <p>
     * 使用 String 序列化器作为 key 序列化器
     * 使用 JSON 序列化器作为 value 序列化器
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @param objectMapper Spring 管理的 ObjectMapper（已配置 JavaTimeModule 等模块）
     * @return RedisTemplate bean
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 String 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用 JSON 序列化器（基于 Spring 管理的 ObjectMapper）
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建 JSON 序列化器
     * <p>
     * 基于 Spring 管理的 ObjectMapper 创建自定义配置的序列化器：
     * <ul>
     *   <li>启用 Default Typing（带白名单）：支持多态类型反序列化</li>
     *   <li>包级白名单：只允许业务包和必要的 JDK 类型</li>
     *   <li>忽略未知属性：避免缓存结构演进时反序列化失败</li>
     *   <li>不将日期写为时间戳：使用 ISO-8601 字符串格式</li>
     * </ul>
     * </p>
     * <p>
     * 注意：这里复制 Spring 管理的 ObjectMapper，保留其已有的模块配置（如 JavaTimeModule），
     * 然后覆盖必要的 Redis 序列化配置。
     * </p>
     *
     * @param objectMapper Spring 管理的 ObjectMapper
     * @return JSON 序列化器
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer(ObjectMapper objectMapper) {
        // 复制 ObjectMapper，避免修改全局配置
        ObjectMapper redisObjectMapper = objectMapper.copy();

        // 可见性设置
        redisObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 禁用将日期写为时间戳，使用 ISO-8601 字符串格式
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 忽略未知属性，避免缓存结构演进时反序列化失败
        redisObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 配置白名单：只允许业务包 + 必要 JDK 类型
        // 这是反序列化安全的关键：避免恶意类注入攻击
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.wewins.fota.")  // 允许所有业务类
                .allowIfSubType("java.util.")        // 允许集合类（List, Map, Set 等）
                .allowIfSubType("java.time.")        // 允许时间类（LocalDateTime 等）
                .build();

        // 启用 Default Typing：在 JSON 中写入 @class 字段，用于类型信息
        // NON_FINAL：只对非 final 字段和类启用类型信息
        redisObjectMapper.activateDefaultTypingAsProperty(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                "@class"
        );

        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }
}
