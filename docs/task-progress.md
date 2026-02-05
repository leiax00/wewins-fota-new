# FOTA 项目开发进度跟踪

> 本文档用于跟踪项目开发进度，记录已完成和待处理的任务。

**项目开始时间**: 2025-01-05
**最后更新时间**: 2025-02-05

---

## 📊 总体进度

```
里程碑 1 (M1): 平台基础 [████████░░░░░░░░░░░] 10% (1/10 天)
  ├─ Day 1: 项目基础架构搭建 ✅ 已完成
  ├─ Day 2: PostgreSQL 数据库架构 ⏳ 进行中
  ├─ Day 3: Redis 缓存架构 ⏸️ 待开始
  ├─ Day 4: 简化认证与授权 ⏸️ 待开始
  └─ Day 5: 集成测试与验收 ⏸️ 待开始
```

---

## ✅ 已完成任务

### 📅 Day 1: 项目基础架构搭建 (2025-02-05)

**状态**: ✅ 已完成
**分支**: `feature/project-structure`
**合并提交**: `c77abd6`
**功能分支**: 已合并到 `develop`

#### 完成内容

##### 1. 项目架构
- ✅ 多模块 Maven 架构（11个模块）
  - fota-bom: 依赖版本管理
  - fota-framework: 框架聚合模块
  - fota-framework-common: 公共 DTO/util
  - fota-framework-database: 数据库层
  - fota-framework-cache: 缓存层
  - fota-framework-mq: 消息队列
  - fota-framework-storage: 对象存储
  - fota-framework-security: 安全认证
  - fota-framework-starter: 自动配置
  - fota-service: Spring Boot 应用

##### 2. 技术栈
- ✅ Spring Boot 3.5.9 (升级修复安全漏洞)
- ✅ Java 21
- ✅ MyBatis-Plus 3.5.15
- ✅ Liquibase (Spring Boot BOM 管理)
- ✅ PostgreSQL 16+ + ClickHouse
- ✅ Redis
- ✅ RabbitMQ
- ✅ AWS S3 SDK 2.25.43
- ✅ Lombok 1.18.36
- ✅ Jackson (Spring Boot 默认)

##### 3. 代码质量
- ✅ BOM 统一版本管理
- ✅ Git Flow 工作流规范（docs/git-workflow.md）
- ✅ Jackson 配置指南（docs/jackson-config.md）
- ✅ Lombok 使用规范（docs/lombok-standards.md）
- ✅ 安全漏洞修复（所有依赖升级到安全版本）

##### 4. Git 操作
- ✅ 使用 `--no-ff` 合并到 develop
- ✅ 创建 `feature/database-schema` 分支
- ✅ 创建任务跟踪文档

---

## 📋 待处理任务

### 📅 Day 2: PostgreSQL 数据库架构 (进行中)

**状态**: ⏳ 准备开始
**分支**: `feature/database-schema`
**开始时间**: 2025-02-05

#### 任务 2-1: 创建 Liquibase changelog 目录结构

**状态**: ⏳ 待开始
**预计时间**: 0.5h
**依赖**: 无

**任务内容**:
- [ ] 创建 `db/changelog/db.changelog-master.yaml`（主文件）
- [ ] 创建 `db/changelog/changes/` 目录
- [ ] 配置 Spring Boot 集成 Liquibase
- [ ] 配置 application.yml 指定 changelog 路径

**验证标准**:
- 目录结构创建完成
- Spring Boot 能识别 changelog 文件

---

#### 任务 2-2: 设计核心表结构

**状态**: ⏸️ 未开始
**预计时间**: 2h
**依赖**: 任务 2-1

**任务内容**:
- [ ] 设计 `products` 表（产品表）
- [ ] 设计 `devices` 表（设备表）
- [ ] 设计 `firmware_versions` 表（固件版本表）
- [ ] 设计 `upgrade_policies` 表（升级策略表）
- [ ] 定义索引和约束

**表结构草案**:

```sql
-- products（产品表）
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255),
    model VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- devices（设备表）
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(255) UNIQUE NOT NULL,
    product_id BIGINT REFERENCES products(id),
    current_version_id BIGINT,
    status VARCHAR(50),
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- firmware_versions（固件版本表）
CREATE TABLE firmware_versions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id),
    version VARCHAR(50) NOT NULL,
    file_url VARCHAR(1024),
    file_size BIGINT,
    md5 VARCHAR(32),
    sha256 VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- upgrade_policies（升级策略表）
CREATE TABLE upgrade_policies (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_version_id BIGINT,
    priority INTEGER DEFAULT 0,
    gray_rate INTEGER DEFAULT 0,
    time_window JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**验证标准**:
- Liquibase 脚本语法正确
- 表创建成功
- 索引和约束生效

---

#### 任务 2-3: 创建 MyBatis-Plus 实体和 Mapper

**状态**: ⏸️ 未开始
**预计时间**: 1.5h
**依赖**: 任务 2-2

**任务内容**:
- [ ] 创建 `Product.java` 实体类
- [ ] 创建 `Device.java` 实体类
- [ ] 创建 `FirmwareVersion.java` 实体类
- [ ] 创建 `UpgradePolicy.java` 实体类
- [ ] 创建对应的 Mapper 接口
- [ ] 配置 MyBatis-Plus

**包结构**:
```
fota-framework-database/src/main/java/com/wewins/fota/database/
├── entity/
│   ├── Product.java
│   ├── Device.java
│   ├── FirmwareVersion.java
│   └── UpgradePolicy.java
└── mapper/
    ├── ProductMapper.java
    ├── DeviceMapper.java
    ├── FirmwareVersionMapper.java
    └── UpgradePolicyMapper.java
```

**实体类示例**:
```java
@Data
@TableName("products")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String manufacturer;
    private String model;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

**验证标准**:
- 实体类使用 Lombok 注解
- Mapper 继承 BaseMapper
- MyBatis-Plus 扫描配置正确

---

#### 任务 2-4: 配置多数据源

**状态**: ⏸️ 未开始
**预计时间**: 1h
**依赖**: 任务 2-3

**任务内容**:
- [ ] 配置 PostgreSQL 主数据源
- [ ] 配置 ClickHouse 分析数据源
- [ ] 创建 DataSourceConfig.java
- [ ] 创建 MyBatisPlusConfig.java
- [ ] 创建 ClickHouseConfig.java
- [ ] 配置 application.yml

**数据源配置**:
```yaml
spring:
  datasource:
    primary:
      jdbc-url: jdbc:postgresql://localhost:5432/fota
      username: fota
      password: fota
    clickhouse:
      jdbc-url: jdbc:clickhouse://localhost:8123/fota_events
```

**验证标准**:
- PostgreSQL 连接正常
- ClickHouse 连接正常
- MyBatis-Plus 能正常操作 PostgreSQL
- JdbcTemplate 能正常查询 ClickHouse

---

#### 任务 2-5: 验证数据库集成

**状态**: ⏸️ 未开始
**预计时间**: 1h
**依赖**: 任务 2-4

**任务内容**:
- [ ] Liquibase 迁移测试
- [ ] MyBatis-Plus CRUD 测试
- [ ] 多数据源测试
- [ ] 编译验证
- [ ] 合并到 develop（使用 --no-ff）

**测试清单**:
- [ ] 执行 Liquibase changelog，表创建成功
- [ ] 插入测试数据到 PostgreSQL
- [ ] MyBatis-Plus 查询测试通过
- [ ] ClickHouse 查询测试通过
- [ ] `mvn clean compile` 成功
- [ ] `mvn clean package` 成功

**Git 操作**:
```bash
# 提交 Day 2 代码
git add .
git commit -m "feat(database): 完成 PostgreSQL 数据库架构

- Liquibase changelog 配置
- 核心表结构（产品/设备/版本/策略）
- MyBatis-Plus 实体和 Mapper
- 多数据源配置（PostgreSQL + ClickHouse）"

# 切换到 develop
git checkout develop

# 合并到 develop（使用 --no-ff）
git merge --no-ff feature/database-schema -m "Merge branch 'feature/database-schema' into develop

完成 Day 2: PostgreSQL 数据库架构

主要变更：
- Liquibase changelog 结构
- 4张核心表
- MyBatis-Plus 集成
- 多数据源配置

合并提交数：5 个任务提交

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# 推送到远程
git push origin develop

# 删除已完成的功能分支
git branch -d feature/database-schema
```

**验证标准**:
- 所有测试通过
- 编译打包成功
- 成功合并到 develop

---

## ⏸️ 待开始任务

### 📅 Day 3: Redis 缓存架构

**状态**: ⏸️ 待开始
**预计开始时间**: Day 2 完成后

**计划任务**:
- [ ] Redis 配置
- [ ] 缓存键设计规范
- [ ] 设备活跃度 Bitmap 实现
- [ ] 策略缓存实现
- [ ] 限流功能

---

### 📅 Day 4: 简化认证与授权

**状态**: ⏸️ 待开始
**预计开始时间**: Day 3 完成后

**计划任务**:
- [ ] JWT 工具类
- [ ] 设备认证接口
- [ ] 简化用户认证
- [ ] API 权限控制

---

### 📅 Day 5: 集成测试与验收

**状态**: ⏸️ 待开始
**预计开始时间**: Day 4 完成后

**计划任务**:
- [ ] 集成测试
- [ ] 性能测试
- [ ] Week 1 验收
- [ ] 文档更新

---

## 📝 变更日志

### 2025-02-05
- ✅ 完成 Day 1: 项目基础架构搭建
- 📋 创建任务跟踪文档
- 🚀 开始 Day 2: PostgreSQL 数据库架构

---

## 🔗 相关文档

- [Git Flow 工作流规范](../docs/git-workflow.md)
- [实施计划](../docs/implementation-plan.md)
- [任务列表](../docs/tasks.md)
- [Jackson 配置指南](../docs/jackson-config.md)
- [Lombok 使用规范](../docs/lombok-standards.md)

---

**注意**:
- 每完成一个任务，更新对应的状态为 ✅
- 每开始一个任务，更新状态为 ⏳
- 每天结束时，更新总体进度
