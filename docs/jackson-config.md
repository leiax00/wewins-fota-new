# Jackson 全局配置说明

## 默认配置（Spring Boot 自动配置）

Spring Boot 3.5.9 已经自动配置了 Jackson，默认行为如下：

```yaml
spring:
  jackson:
    # 日期时间格式
    date-format: yyyy-MM-dd HH:mm:ss

    # 时区设置（推荐使用 UTC）
    time-zone: UTC

    # 空值处理
    default-property-inclusion: non_null

    # 序列化选项
    serialization:
      # 写入日期为时间戳（false = 使用字符串格式）
      write-dates-as-timestamps: false

    # 反序列化选项
    deserialization:
      # 忽略未知属性
      fail-on-unknown-properties: false
```

## 推荐配置（application.yml）

```yaml
spring:
  jackson:
    # 日期时间格式化
    date-format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
    time-zone: UTC

    # 空值处理
    default-property-inclusion: non_null

    # 序列化行为
    serialization:
      write-dates-as-timestamps: false
      # 忽略 null 值
      write-null-map-values: false
      # 忽略空集合
      write-empty-json-arrays: false

    # 反序列化行为
    deserialization:
      # 忽略 JSON 中包含但 Java 对象不存在的属性
      fail-on-unknown-properties: false

    # Parser 配置
    parser:
      # 允许注释（生产环境建议关闭）
      allow-comments: false
      # 允许非引号控制字符
      allow-unquoted-control-chars: false

    # Generator 配置
    generator:
      # 使用规范的 JSON（不转义非 ASCII 字符）
      escape-non-ascii: false
```

## Java 配置类示例

如果需要更精细的控制，可以创建配置类：

```java
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.createXmlMapper(false)
            .build();

        // 注册自定义模块
        mapper.registerModule(new JavaTimeModule());

        // 配置序列化
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 配置时区
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 日期格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
```

## 常用注解

```java
import com.fasterxml.jackson.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceDto {

    @JsonProperty("device_id")  // 重命名字段
    private String deviceId;

    @JsonInclude(JsonInclude.Include.NON_NULL)  // null 不序列化
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")  // 日期格式
    private LocalDateTime lastSeen;

    @JsonIgnore  // 完全忽略此字段
    private String internalNote;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)  // 只读
    private String createdAt;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)  // 只写
    private String password;
}
```

## MyBatis-Plus JSON 字段处理

### 1. PostgreSQL JSONB 字段

```java
@Data
@TableName("devices")
public class Device {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String imei;

    // 使用 JSON 类型处理器
    @TableField(typeHandler = JacksonTypeHandler.class)
    private DeviceMetadata metadata;
}

@Data
public class DeviceMetadata {
    private String model;
    private String osVersion;
    private Map<String, Object> attributes;
}
```

### 2. 自定义 TypeHandler

```java
@MappedTypes({Object.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JacksonTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i,
            Object parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        return fromJson(rs.getString(columnName));
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object fromJson(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

## 性能优化建议

1. **单例 ObjectMapper**：Jackson 的 ObjectMapper 是线程安全的，应该全局复用
2. **避免频繁创建**：不要在每个方法中创建新的 ObjectMapper
3. **使用 @JsonView**：控制不同场景的字段序列化
4. **缓存反射元数据**：Jackson 会缓存序列化/反序列化的元数据

## 参考资料

- Jackson 官方文档：https://fasterxml.github.io/jackson-databind/javadoc/2.13/
- Spring Boot JSON 配置：https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.servlet.jackson
- MyBatis-Plus TypeHandler：https://baomidou.com/pages/65e7d1/
