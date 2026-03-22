# Deprecated

`db/changelog/` 中的 Liquibase 文件已冻结，不再作为应用启动时的数据库迁移入口。

当前运行时迁移方案已切换为 Flyway：

- MySQL: `classpath:db/migration/mysql`
- PostgreSQL: `classpath:db/migration/postgresql`

保留本目录的原因：

- 作为历史迁移记录备查
- 便于对照已有 SQL 基线
- 迁移 Flyway 期间避免一次性删除历史文件带来信息丢失

约束：

- 新增数据库迁移时，不要再修改 `db/changelog/`
- 新增数据库迁移时，只在 `db/migration/<vendor>/` 下新增 `V{version}__*.sql`
- 如果后续确认不再需要 Liquibase 历史资料，可单独发起一次清理
