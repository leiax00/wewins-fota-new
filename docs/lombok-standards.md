# Lombok 使用规范

## 版本

- **Lombok**: 1.18.36
- **Java**: 21
- **Maven Compiler Plugin**: 3.13.0

## 配置状态

✅ Maven 依赖已配置
✅ 编译器注解处理器已启用
✅ `lombok.config` 配置文件已创建

## IDEA 配置

### 1. 安装插件

```
Settings → Plugins → 搜索 "Lombok" → 安装
```

### 2. 启用注解处理

```
Settings → Build, Execution, Deployment →
Compiler → Annotation Processors →
勾选 "Enable annotation processing"
```

### 3. 启用注解处理器（新版本 IDEA）

```
Settings → Build, Execution, Deployment →
Compiler → Annotation Processors →
Enable annotation processing: ✅
```

## 推荐使用的注解

### ✅ 推荐使用

| 注解 | 使用场景 | 说明 |
|------|---------|------|
| `@Getter` / `@Setter` | 所有实体、DTO | 生成 getter/setter 方法 |
| `@RequiredArgsConstructor` | Service、Controller | 生成构造函数（final 字段） |
| `@Builder` | DTO、请求对象 | 构建者模式，适合多参数对象 |
| `@Slf4j` | 需要日志的类 | 自动生成 log 对象 |
| `@ToString` | 实体类 | 生成 toString 方法 |
| `@EqualsAndHashCode` | 实体类 | 生成 equals/hashCode 方法 |
| `@AllArgsConstructor` | 枚举、常量类 | 生成全参构造函数 |
| `@NoArgsConstructor` | JPA 实体、序列化 | 生成无参构造函数 |

### ⚠️ 谨慎使用

| 注解 | 风险 | 建议 |
|------|------|------|
| `@Data` | 可能生成不需要的方法、隐藏副作用 | **只用于简单 DTO**，不用于复杂实体 |
| `@Value` | 不可变对象，但字段是 final | 适合配置类、常量类 |
| `@Cleanup` | 资源管理，Java 7+ try-with-resources 更好 | 不推荐，使用 try-with-resources |
| `@SneakyThrows` | 隐藏异常，不利于错误处理 | 不推荐 |

### ❌ 禁止使用

| 注解 | 原因 |
|------|------|
| `@Delegate` | 代码可读性差，难以维护 |
| `@ExtensionMethod` | 不稳定，可能影响代码可读性 |

## 使用示例

### 1. DTO（推荐使用 Builder）

```java
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDto {
    private Long id;
    private String imei;
    private String model;
    private Integer batteryLevel;
}
```

### 2. 实体类（MyBatis-Plus）

```java
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("devices")
public class Device {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String imei;
    private String model;

    // @Data 会生成所有方法，对于实体类是安全的
    // 因为 MyBatis-Plus 不需要复杂的业务逻辑
}
```

### 3. Service 类（推荐构造函数注入）

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor  // 生成构造函数（final 字段）
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public Device getDevice(String imei) {
        log.info("Fetching device: {}", imei);
        return deviceMapper.selectByImei(imei);
    }
}
```

### 4. Controller 类

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/{imei}")
    public DeviceDto getDevice(@PathVariable String imei) {
        return deviceService.getDevice(imei);
    }
}
```

### 5. 枚举类

```java
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum DeviceStatus {
    ONLINE("在线"),
    OFFLINE("离线"),
    UPGRADING("升级中");

    private final String description;
}
```

## 最佳实践

### 1. 避免在复杂领域模型使用 @Data

```java
// ❌ 不推荐：复杂业务逻辑类
@Data
public class DeviceUpgradeService {
    // 大量业务方法...
    // @Data 会生成 equals/hashCode，可能导致意外行为
}

// ✅ 推荐：只使用需要的功能
@RequiredArgsConstructor
public class DeviceUpgradeService {
    private final DeviceMapper deviceMapper;
    private final UpgradePolicyChecker policyChecker;

    // 业务方法...
}
```

### 2. DTO 使用 Builder 模式

```java
// ✅ 推荐：DTO 使用 Builder
DeviceDto dto = DeviceDto.builder()
    .id(1L)
    .imei("123456789012345")
    .model("iPhone 13")
    .batteryLevel(85)
    .build();
```

### 3. 日志使用 @Slf4j

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceService {
    public void updateDevice(DeviceDto dto) {
        log.info("Updating device: {}", dto.getImei());
        log.debug("Device details: {}", dto);
        log.error("Failed to update device", exception);
    }
}
```

### 4. 配置类使用 @ConfigurationProperties

```java
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fota.upgrade")
public class UpgradeProperties {
    private int maxConcurrentUpgrades;
    private int timeoutSeconds;
    private List<String> allowedModels;
}
```

## 注意事项

### 1. @Data 的陷阱

```java
@Data
public class Parent {
    private Long id;
}

@Data
public class Child extends Parent {
    private String name;
}

// ⚠️ 注意：Child 的 @EqualsAndHashCode 不会包含 Parent 的字段
// 解决方案：显式指定 callSuper
@EqualsAndHashCode(callSuper = true)
@Data
public class Child extends Parent {
    private String name;
}
```

### 2. @Builder 与 @NoArgsConstructor / @AllArgsConstructor

```java
@Data
@Builder  // 会生成全参构造函数
@NoArgsConstructor  // 需要显式添加无参构造函数
@AllArgsConstructor
public class DeviceDto {
    private Long id;
    private String imei;
}
```

### 3. Lombok 与 Jackson

```java
@Data
public class DeviceDto {
    @JsonProperty("device_id")  // Jackson 注解优先
    private String deviceId;
}

// Lombok 生成的 getter/getDeviceId() 会与 Jackson 一起正常工作
```

### 4. Lombok 与 MapStruct

如果同时使用 MapStruct，需要调整编译器配置：

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
    </path>
    <path>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>${mapstruct.version}</version>
    </path>
</annotationProcessorPaths>
```

## 编译验证

### Maven 编译

```bash
mvn clean compile
```

### 检查生成的代码

在 IDEA 中：
```
Tools → Show Lombok Bytecode
```

或者在 target 目录查看生成的 `.class` 文件。

## 故障排查

### 问题 1：找不到 getter/setter 方法

**原因**：IDEA 未安装插件或未启用注解处理

**解决**：
1. 检查插件是否安装
2. 启用注解处理
3. 重新构建项目

### 问题 2：编译时 Lombok 不生效

**原因**：Maven 编译器未配置注解处理器

**解决**：检查根 `pom.xml` 中的 `maven-compiler-plugin` 配置

### 问题 3：IDEA 提示符号找不到

**原因**：IDEA 未识别 Lombok 生成的代码

**解决**：
```
File → Invalidate Caches / Restart
```

## 参考资料

- Lombok 官方文档：https://projectlombok.org/features/all
- Lombok 配置：https://projectlombok.org/features/configuration
- IDEA Lombok 插件：https://plugins.jetbrains.com/plugin/6317-lombok
