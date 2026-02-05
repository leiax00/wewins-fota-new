package com.wewins.fota.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL JSONB 类型处理器（JsonNode）
 * <p>
 * 用于在 PostgreSQL JSONB 字段与 Java JsonNode 之间进行序列化/反序列化。
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
 * 异常处理：
 * Jackson 序列化/反序列化失败会包装为 SQLException 抛出，
 * 便于 MyBatis 统一处理并保留原始错误信息。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    /**
     * Jackson ObjectMapper，线程安全
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JsonNode to JSON string: " + e.getMessage(), e);
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
            throw new SQLException("Failed to deserialize JSON string to JsonNode: " + e.getMessage(), e);
        }
    }
}
