# Flyway Migration Guide

## 当前状态

项目已从 Liquibase 切换为 Flyway。

运行时入口：

- `spring.flyway.locations=classpath:db/migration/{vendor}`

目录结构：

- PostgreSQL: `fota-service/src/main/resources/db/migration/postgresql`
- MySQL: `fota-service/src/main/resources/db/migration/mysql`

## 迁移规则

1. 新增迁移只允许放在 `db/migration/<vendor>/`
2. 文件命名必须使用 `V{version}__{description}.sql`
3. PostgreSQL 和 MySQL 的结构性迁移应尽量保持同版本号、同语义
4. 如果某个版本只适用于单一数据库，需在另一个数据库目录中补充说明或保留版本连续性策略
5. 软删除统一使用 `deleted SMALLINT NOT NULL DEFAULT 0`

## 现有版本

### MySQL

- `V1__baseline.sql`
- `V2__initdata.sql`
- `V4__change_deleted_column.sql`
- `V5__add_relation_tables.sql`

### PostgreSQL

- `V1__baseline.sql`
- `V2__initdata.sql`
- `V3__reset_sequences_after_initdata.sql`
- `V4__change_deleted_column.sql`
- `V5__add_relation_tables.sql`

## 基线策略

当前配置启用了：

- `baseline-on-migrate=true`
- `baseline-version=5`

含义：

- 对已经存在业务表、但没有 Flyway 历史表的旧库，Flyway 会以版本 `5` 建立基线
- 这适用于“数据库实际状态已等价于当前 V1-V5 之后”的场景

如果目标数据库仍停留在更早阶段，不能直接使用这个默认值，需要先人工校准：

- 要么先补齐旧库结构
- 要么调整 Flyway baseline 配置

## 旧目录说明

`fota-service/src/main/resources/db/changelog` 已冻结，只保留为历史参考，不再作为运行时迁移入口。
