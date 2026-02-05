# Day 2: PostgreSQL 数据库架构

> **任务编号**: T02
> **所属 Sprint**: Sprint 1
> **所属 Day**: Day 2
> **状态**: ⏳ In Progress

---

## 📋 基本信息

**负责人**: FOTA 团队
**开始日期**: 2026-02-05
**预计完成**: 2026-02-06
**预计工时**: 8 小时

---

## 🎯 背景与目标

### 背景
需要搭建 PostgreSQL 数据库架构，创建核心业务表结构，并集成 MyBatis-Plus 实现数据访问层。

### 目标
- 搭建 Liquibase 数据库迁移框架
- 设计并创建核心表结构
- 集成 MyBatis-Plus ORM 框架
- 配置多数据源（PostgreSQL + ClickHouse）

### 范围
- ✅ Liquibase changelog 配置
- ✅ 核心表结构（4张表）
- ✅ MyBatis-Plus 实体和 Mapper
- ✅ 多数据源配置
- ❌ 不包含业务逻辑实现
- ❌ 不包含复杂查询优化

### 非目标
- ❌ 完整的业务逻辑
- ❌ 性能优化
- ❌ 分库分表

---

## 📦 交付物

### 数据库脚本
- [ ] `db/changelog/db.changelog-master.yaml`
- [ ] `db/changelog/changes/V1__init_core.sql`
- [ ] `docs/schema-overview.md`

### 代码
- [ ] 实体类（4个）
- [ ] Mapper 接口（4个）
- [ ] 数据源配置类

### 配置文件
- [ ] `application.yml` 数据源配置
- [ ] Liquibase 配置

---

## 🔨 任务拆解

### 任务 2-1: 创建 Liquibase changelog 结构 (30分钟)
- [ ] 创建 `db/changelog/` 目录
- [ ] 创建 `db.changelog-master.yaml`
- [ ] 配置 Spring Boot 集成

**验收标准**:
- [ ] 目录结构正确
- [ ] Spring Boot 能识别 changelog

### 任务 2-2: 设计核心表结构 (2小时)
- [ ] 设计 products 表
- [ ] 设计 devices 表
- [ ] 设计 firmware_versions 表
- [ ] 设计 upgrade_policies 表

**验收标准**:
- [ ] 表结构符合业务需求
- [ ] 主键、外键设计合理
- [ ] 索引设计正确

### 任务 2-3: 创建 MyBatis-Plus 实体和 Mapper (2小时)
- [ ] 创建 Product.java
- [ ] 创建 Device.java
- [ ] 创建 FirmwareVersion.java
- [ ] 创建 UpgradePolicy.java
- [ ] 创建对应的 Mapper 接口

**验收标准**:
- [ ] 实体使用 Lombok 注解
- [ ] Mapper 继承 BaseMapper
- [ ] MyBatis-Plus 扫描配置正确

### 任务 2-4: 配置多数据源 (2小时)
- [ ] 配置 PostgreSQL 主数据源
- [ ] 配置 ClickHouse 分析数据源
- [ ] 创建 DataSourceConfig.java
- [ ] 创建 MyBatisPlusConfig.java

**验收标准**:
- [ ] PostgreSQL 连接正常
- [ ] ClickHouse 连接正常
- [ ] 两个数据源独立工作

### 任务 2-5: 验证数据库集成 (1.5小时)
- [ ] Liquibase 迁移测试
- [ ] MyBatis-Plus CRUD 测试
- [ ] 编译验证
- [ ] 合并到 develop

**验收标准**:
- [ ] 表创建成功
- [ ] CRUD 操作正常
- [ ] 编译通过
- [ ] 成功合并

---

## ✅ 测试清单

### Liquibase 测试
- [ ] 执行 changelog 成功
- [ ] 表结构正确创建
- [ ] 索引和约束生效

### MyBatis-Plus 测试
- [ ] 插入数据成功
- [ ] 查询数据成功
- [ ] 更新数据成功
- [ ] 删除数据成功

### 多数据源测试
- [ ] PostgreSQL 操作正常
- [ ] ClickHouse 查询正常

---

## 🚧 技术要点

### Liquibase 配置

#### application.yml
```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
    default-schema: public
```

#### db.changelog-master.yaml
```yaml
databaseChangeLog:
  - changeSet:
      id: V1__init_core
      author: fota-team
      changes:
        - sqlFile:
            path: changes/V1__init_core.sql
            relativeToChangelogFile: true
```

### 核心表结构

#### products (产品表)
```sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255),
    model VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_manufacturer ON products(manufacturer);
```

#### devices (设备表)
```sql
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(255) UNIQUE NOT NULL,
    product_id BIGINT REFERENCES products(id),
    current_version_id BIGINT,
    status VARCHAR(50) NOT NULL,
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_imei ON devices(imei);
CREATE INDEX idx_devices_product_id ON devices(product_id);
CREATE INDEX idx_devices_status ON devices(status);
```

#### firmware_versions (固件版本表)
```sql
CREATE TABLE firmware_versions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id),
    version VARCHAR(50) NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL,
    md5 VARCHAR(32) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_fv_product_version ON firmware_versions(product_id, version);
CREATE INDEX idx_fv_product_id ON firmware_versions(product_id);
```

#### upgrade_policies (升级策略表)
```sql
CREATE TABLE upgrade_policies (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_version_id BIGINT REFERENCES firmware_versions(id),
    priority INTEGER DEFAULT 0,
    gray_rate INTEGER DEFAULT 0,
    time_window JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_product_id ON upgrade_policies(product_id);
CREATE INDEX idx_up_priority ON upgrade_policies(priority);
```

### MyBatis-Plus 实体示例

```java
@Data
@TableName("products")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String manufacturer;
    private String model;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### 多数据源配置

#### application.yml
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

---

## ⚠️ 风险与依赖

### 依赖任务
- 前置: Day 1 项目基础架构搭建 ✅

### 风险点
| 风险 | 影响 | 概率 | 缓解措施 | 状态 |
|------|------|------|----------|------|
| PostgreSQL 不熟悉 | 中 | 中 | 提前学习，参考 playbooks | ⏸️ |
| 多数据源配置复杂 | 中 | 中 | 参考官方文档，先配置 PostgreSQL | ⏸️ |
| 时间不够 | 高 | 低 | Must-have 优先，Nice-to-have 砍删 | ⏸️ |

---

## 📝 实施笔记

### 学习资源
- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Liquibase 官方文档](https://docs.liquibase.com/)

### 关键配置
- MyBatis-Plus 扫描包: `com.wewins.fota.database`
- Liquibase changelog 路径: `classpath:db/changelog/db.changelog-master.yaml`
- 主数据源: `spring.datasource.primary`

---

## 📊 当前进度

```
Day 2 进度: [░░░░░░░░░░░░░░░░░] 0%

任务 2-1: 创建 Liquibase changelog 结构        [░░░░░░░░░░░░░░░░░░░░] 0%
任务 2-2: 设计核心表结构                     [░░░░░░░░░░░░░░░░░░░░] 0%
任务 2-3: 创建 MyBatis-Plus 实体和 Mapper        [░░░░░░░░░░░░░░░░░░░░░] 0%
任务 2-4: 配置多数据源                       [░░░░░░░░░░░░░░░░░░░░░] 0%
任务 2-5: 验证数据库集成                     [░░░░░░░░░░░░░░░░░░░░] 0%
```

---

## 🔗 相关资源

### 设计文档
- [Sprint 1 计划](../../05-plans/sprint-1.md)
- [任务进度](day-02-progress.md)

### 技术文档
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Liquibase 官方文档](https://docs.liquibase.com/)
- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)

---

**任务开始时间**: 2026-02-05 19:00
**当前状态**: ⏳ In Progress
