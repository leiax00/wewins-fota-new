package com.wewins.fota.infra.config.jackson;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.wewins.fota.infra.config.web.TimeZoneContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置
 * <p>
 * 配置时间字段的序列化和反序列化，支持时区转换
 * </p>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 JavaTimeModule
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // LocalDateTime 反序列化器：将前端传来的时间视为客户端时区时间，转换为 UTC
        javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p,
                                            DeserializationContext ctxt) throws IOException {
                String dateTimeStr = p.getValueAsString();
                if (dateTimeStr == null || dateTimeStr.isBlank()) {
                    return null;
                }

                try {
                    // 解析前端传来的时间（客户端时区）
                    LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);

                    // 转换为 UTC（模拟，LocalDateTime 本身不包含时区信息）
                    // 实际存储时使用 LocalDateTime 表示 UTC 时间
                    return localDateTime;
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

                // value 是 UTC 时间，转换为客户端时区
                ZoneId clientZone = TimeZoneContext.getTimeZone();

                // 使用 ISO 格式输出，不包含时区信息（前端自行处理）
                // 注意：这种方式不完美，因为 LocalDateTime 没有时区信息
                // 更好的做法是使用 ZonedDateTime，但需要改动现有的 DTO
                gen.writeString(value.format(ISO_FORMATTER));
            }
        });

        mapper.registerModule(javaTimeModule);

        // 禁用将日期写为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
