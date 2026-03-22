package com.wewins.fota.database.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * JSON 字符串类型处理器
 * <p>
 * 支持 PostgreSQL JSONB 和 MySQL JSON 类型
 * 使用 setString 方式，兼容多种数据库
 * </p>
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        Connection connection = ps.getConnection();
        String databaseProductName = connection == null || connection.getMetaData() == null
                ? ""
                : connection.getMetaData().getDatabaseProductName();

        if ("PostgreSQL".equalsIgnoreCase(databaseProductName)) {
            ps.setObject(i, parameter, Types.OTHER);
            return;
        }

        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
