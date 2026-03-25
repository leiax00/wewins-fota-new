package com.wewins.fota.database.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 多数据库 JSON 类型处理器（JsonNode）
 * <p>
 * 支持 PostgreSQL JSONB 和 MySQL JSON 字段与 Java JsonNode 之间的序列化/反序列化。
 * 通过 Jackson ObjectMapper 处理 JSON 数据。
 * </p>
 *
 * <p>
 * 使用方式：
 * <pre>
 * &#64;TableField(typeHandler = JsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
 * private JsonNode meta;
 * </pre>
 * </p>
 *
 * <p>
 * 数据库兼容性：
 * <ul>
 *   <li>PostgreSQL：使用 Types.OTHER 传递 JSONB</li>
 *   <li>MySQL：使用 setString 传递 JSON（MySQL 会自动转换）</li>
 * </ul>
 * </p>
 *
 * <p>
 * 异常处理：
 * Jackson 序列化/反序列化失败会包装为 SQLException 抛出，
 * 便于 MyBatis 统一处理并保留原始错误信息。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Slf4j
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    private static final String JSON_NULL_SUMMARY = "null";

    /**
     * Jackson ObjectMapper，线程安全
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            String jsonString = OBJECT_MAPPER.writeValueAsString(parameter);
            // 根据数据库类型选择不同的参数设置方式
            if (isPostgreSQL(ps)) {
                // PostgreSQL: 使用 Types.OTHER 传递 JSONB 类型
                ps.setObject(i, jsonString, java.sql.Types.OTHER);
            } else {
                // MySQL 及其他数据库: 使用 setString，数据库会自动转换
                ps.setString(i, jsonString);
            }
        } catch (JsonProcessingException e) {
            log.error("JsonNode 序列化失败: message={}", e.getMessage(), e);
            throw new SQLException("Failed to serialize JsonNode to JSON string: " + e.getMessage(), e);
        }
    }

    /**
     * 判断当前数据库是否为 PostgreSQL
     *
     * @param ps PreparedStatement
     * @return 是否为 PostgreSQL
     */
    private boolean isPostgreSQL(PreparedStatement ps) {
        try {
            DatabaseMetaData metaData = ps.getConnection().getMetaData();
            String databaseProductName = metaData.getDatabaseProductName();
            return "PostgreSQL".equalsIgnoreCase(databaseProductName);
        } catch (SQLException e) {
            log.warn("无法获取数据库类型信息，默认使用 MySQL 兼容模式: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    /**
     * 将 JSON 字符串解析为 JsonNode
     *
     * @param json JSON 字符串
     * @return JsonNode 对象，如果 json 为空则返回 null
     * @throws SQLException 解析失败时抛出
     */
    private JsonNode parseJson(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: message={}, summary={}", e.getMessage(), summarizeJson(json), e);
            throw new SQLException("Failed to deserialize JSON string to JsonNode: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 JSON 字符串的摘要信息（避免日志泄露敏感数据）
     *
     * @param json JSON 字符串
     * @return 摘要信息，包含长度
     */
    private String summarizeJson(String json) {
        if (json == null) {
            return JSON_NULL_SUMMARY;
        }
        int len = json.length();
        return "len=" + len;
    }
}
