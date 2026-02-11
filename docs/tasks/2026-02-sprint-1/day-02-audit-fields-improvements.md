# Day 2 - 审计字段和软删除改进

## 审查结果总结

### 代码 Review 日期
2026-02-05

### 审查来源
Codex AI 代码审查

---

## 发现的问题

### 1. [High - 已修复] MyBatis-Plus 配置路径错误

**问题描述**：
`mybatis-plus` 配置被放在了 `spring:` 节点下，应该是顶层配置，否则可能不生效。

**修复方案**：
- 将 `mybatis-plus` 配置移到 `application.yml` 的顶层
- 确保配置在 `spring:` 节点之外

**文件**：
- `fota-service/src/main/resources/application.yml`

---

### 2. [High - 已缓解] 逻辑删除 NULL 值处理

**问题描述**：
`logic-not-delete-value: "NULL"` 可能生成错误的 SQL（`deleted_at = NULL` 而非 `deleted_at IS NULL`）。

**当前处理**：
- 保留全局配置和实体类上的 `@TableLogic` 注解
- 在实际使用中需要验证生成的 SQL 是否正确
- 如果出现问题，考虑以下备选方案：
  1. 使用数值型逻辑删除字段（0/1）
  2. 在 QueryWrapper 中显式使用 `isNull("deletedAt")`
  3. 放弃全局逻辑删除配置

**验证方法**：
```java
// 测试查询是否自动添加 IS NULL 条件
productMapper.selectList(new QueryWrapper<>());
// 检查生成的 SQL 是否包含: WHERE deleted_at IS NULL
```

**文件**：
- `fota-service/src/main/resources/application.yml`
- 所有实体类（Product, Device, FirmwareVersion, UpgradePolicy）

---

### 3. [Medium - 已缓解] UserContext 异步场景内存泄漏风险

**问题描述**：
`UserContext` 使用 `ThreadLocal` + 拦截器清理，在同步 Web 请求中安全，但在异步场景（@Async、MQ 消费、定时任务）中可能泄漏。

**修复方案**：
- 添加了 `runWithUser(Long userId, Runnable task)` 工具方法
- 添加了 `runWithUser(Long userId, Callable<T> task)` 工具方法（带返回值）
- 在异步场景下使用这些方法确保正确清理

**使用示例**：
```java
// 在 MQ 消费者中使用
@RabbitListener(queues = "upgrade.queue")
public void handleMessage(UpgradeMessage message) {
    UserContext.runWithUser(message.getUserId(), () -> {
        // 业务逻辑
        processUpgrade(message);
    });
}

// 在 @Async 方法中使用
@Async
public void asyncProcess(Long userId, UpgradeData data) {
    UserContext.runWithUser(userId, () -> {
        // 异步业务逻辑
        doProcess(data);
    });
}
```

**文件**：
- `fota-service/src/main/java/com/wewins/fota/config/UserContext.java`

---

### 4. [Low - 已修复] SQL 中重复的唯一索引

**问题描述**：
`firmware_versions` 表中已创建 `UNIQUE` 约束，PostgreSQL 会自动为约束创建索引，不需要显式再创建一次。

**修复方案**：
- 移除了显式创建 `uk_fv_product_version` 索引的语句
- 添加注释说明索引由约束自动创建

**文件**：
- `fota-service/src/main/resources/db/changelog/changes/V1__init_core.sql`

---

## 改进建议

### 1. 考虑引入 BaseEntity

如果后续有更多实体类需要审计字段，可以考虑抽取 `BaseEntity`：

```java
@Data
public abstract class BaseEntity implements Serializable {
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
```

**优点**：
- 减少重复代码
- 统一审计字段管理

**缺点**：
- 增加继承层次
- 某些表可能不需要全部审计字段

**建议**：当实体类超过 10 个时再考虑引入。

---

### 2. 增强 UserContext 安全性

可以考虑以下增强：

1. **使用 TransmittableThreadLocal**：
   - 支持线程池场景下的上下文传递
   - 需要引入阿里 TTL 库

2. **添加上下文复制功能**：
   ```java
   public static UserContextSnapshot capture() {
       return new UserContextSnapshot(getCurrentUserId());
   }

   public static void restore(UserContextSnapshot snapshot) {
       setCurrentUserId(snapshot.getUserId());
   }
   ```

3. **集成 Spring Security**：
   ```java
   public static Long getCurrentUserId() {
       Authentication auth = SecurityContextHolder.getContext().getAuthentication();
       if (auth != null && auth.isAuthenticated()) {
           return Long.parseLong(auth.getName());
       }
       return null;
   }
   ```

**建议**：根据实际需求逐步增强。

---

## 待确认问题

### 1. MyBatis-Plus 版本

**问题**：当前项目使用的 MyBatis-Plus 版本对 `logic-not-delete-value: "NULL"` 的处理方式。

**确认方法**：
```xml
<!-- 检查 pom.xml 中的版本 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>?</version>
</dependency>
```

**行动**：需要在测试环境中验证生成的 SQL。

---

### 2. 逻辑删除的作用域

**问题**：逻辑删除是否需要对所有查询默认生效？

**选项**：
- A. 所有查询默认过滤已删除数据（推荐）
- B. 管理端可以查看已删除数据，设备端不可见

**建议**：
- 选项 A：使用当前的 @TableLogic 全局配置
- 选项 B：在管理端使用自定义 SQL 查询全部数据

---

### 3. 异步场景的使用频率

**问题**：是否会在异步/线程池/MQ 消费场景中使用 `UserContext`？

**影响**：
- 如果很少使用，当前的 `runWithUser` 方案足够
- 如果频繁使用，考虑引入 `TransmittableThreadLocal`

**建议**：根据实际业务场景决定。

---

## 验证清单

在合并代码前，请确保：

- [x] MyBatis-Plus 配置路径已修复
- [x] SQL 重复索引已移除
- [x] UserContext 添加了 `runWithUser` 方法
- [ ] 测试验证逻辑删除生成的 SQL 是否正确（`IS NULL` vs `= NULL`）
- [ ] 在异步场景（MQ 消费者、@Async）中使用 `runWithUser` 方法
- [ ] 编写集成测试验证审计字段自动填充

---

## 参考资料

- [MyBatis-Plus 逻辑删除官方文档](https://baomidou.com/pages/8b2c3e/)
- [ThreadLocal 内存泄漏问题](https://stackoverflow.com/questions/17958835/threadlocal-memory-leak)
- [PostgreSQL 部分索引](https://www.postgresql.org/docs/current/indexes-partial.html)

---

**最后更新**：2026-02-05
**审查人**：Codex AI
**修复人**：FOTA Team
