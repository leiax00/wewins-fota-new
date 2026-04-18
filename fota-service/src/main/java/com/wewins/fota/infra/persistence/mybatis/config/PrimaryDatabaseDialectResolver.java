package com.wewins.fota.infra.persistence.mybatis.config;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Locale;

/**
 * 识别主库方言，供需要区分 PostgreSQL / MySQL 的持久化逻辑复用。
 */
@Component
public class PrimaryDatabaseDialectResolver {

    private final DatabaseDialect databaseDialect;

    public PrimaryDatabaseDialectResolver(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            this.databaseDialect = DatabaseDialect.fromProductName(productName);
        } catch (Exception e) {
            throw new IllegalStateException("识别数据库类型失败", e);
        }
    }

    public boolean isPostgres() {
        return databaseDialect == DatabaseDialect.POSTGRESQL;
    }

    public boolean isMySql() {
        return databaseDialect == DatabaseDialect.MYSQL;
    }

    public DatabaseDialect getDatabaseDialect() {
        return databaseDialect;
    }

    public enum DatabaseDialect {
        POSTGRESQL,
        MYSQL;

        static DatabaseDialect fromProductName(String productName) {
            String normalized = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
            if (normalized.contains("postgres")) {
                return POSTGRESQL;
            }
            if (normalized.contains("mysql")) {
                return MYSQL;
            }
            throw new IllegalStateException("不支持的数据库类型: " + productName);
        }
    }
}
