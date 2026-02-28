package com.wewins.fota.infra.config.jackson;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wewins.fota.infra.config.web.TimeZoneContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置
 * <p>
 * 配置时间字段的序列化和反序列化，支持时区转换
 * </p>
 * <p>
 * <strong>时区处理策略</strong>：
 * <ul>
 *   <li>数据库存储：LocalDateTime 表示 UTC 时间</li>
 *   <li>前端发送：ISO8601 格式字符串（自动处理时区）</li>
 *   <li>前端接收：客户端本地时间字符串（后端自动转换）</li>
 * </ul>
 * </p>
 * <p>
 * <strong>格式支持</strong>：
 * <ul>
 *   <li>{@code 2026-02-27T16:30:00} - 客户端本地时间（不带时区）</li>
 *   <li>{@code 2026-02-27T16:30:00Z} - UTC 时间（带 Z 后缀）</li>
 *   <li>{@code 2026-02-27T16:30:00+08:00} - 带时区偏移的时间</li>
 * </ul>
 * </p>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 JavaTimeModule
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // LocalDateTime 反序列化器：支持多种时间格式
        javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p,
                                            DeserializationContext ctxt) throws IOException {
                String dateTimeStr = p.getValueAsString();
                if (dateTimeStr == null || dateTimeStr.isBlank()) {
                    return null;
                }

                try {
                    // 检查是否包含时区信息（Z 或 +HH:MM）
                    if (dateTimeStr.endsWith("Z")) {
                        // UTC 时间（带 Z 后缀），直接解析并存储
                        return LocalDateTime.parse(dateTimeStr.replace("Z", ""), ISO_FORMATTER);
                    } else if (dateTimeStr.contains("+") || dateTimeStr.lastIndexOf("-") > 10) {
                        // 带时区偏移的时间（如 +08:00）
                        Instant instant = Instant.parse(dateTimeStr);
                        return LocalDateTime.ofInstant(instant, UTC);
                    } else {
                        // 客户端本地时间（不带时区），需要转换为 UTC
                        LocalDateTime clientLocalDateTime = LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);
                        ZoneId clientZone = TimeZoneContext.getTimeZone();
                        ZonedDateTime clientZoned = clientLocalDateTime.atZone(clientZone);
                        ZonedDateTime utcZoned = clientZoned.withZoneSameInstant(UTC);
                        return utcZoned.toLocalDateTime();
                    }
                } catch (Exception e) {
                    throw new IOException("无法解析时间: " + dateTimeStr, e);
                }
            }
        });

        // LocalDateTime 序列化器：将 UTC 时间转换为客户端时区时间
        javaTimeModule.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value,
                                 com.fasterxml.jackson.core.JsonGenerator gen,
                                 com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                if (value == null) {
                    gen.writeNull();
                    return;
                }

                // value 是 UTC 时间（LocalDateTime 不包含时区，约定为 UTC）
                // 转换为客户端时区
                ZoneId clientZone = TimeZoneContext.getTimeZone();

                // 将 UTC LocalDateTime 视为 UTC 时间，转换为客户端时区
                ZonedDateTime utcZoned = value.atZone(UTC);
                ZonedDateTime clientZoned = utcZoned.withZoneSameInstant(clientZone);

                // 输出客户端本地时间（ISO 格式，不含时区信息）
                gen.writeString(clientZoned.format(ISO_FORMATTER));
            }
        });

        mapper.registerModule(javaTimeModule);

        // 禁用将日期写为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
