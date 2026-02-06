package com.wewins.fota.database.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL JSONB 类型处理器（List&lt;String&gt;）
 * <p>
 * 用于在 PostgreSQL JSONB 字段与 Java List&lt;String&gt; 之间进行序列化/反序列化。
 * 通过 Jackson ObjectMapper 将 List&lt;String&gt; 转为 JSON 数组字符串。
 * </p>
 *
 * <p>
 * 使用方式：
 * <pre>
 * &#64;TableField(typeHandler = JsonbListStringTypeHandler.class, jdbcType = JdbcType.VARCHAR)
 * private List&lt;String&gt; tags;
 * </pre>
 * </p>
 *
 * <p>
 * 数据库存储格式：
 * <pre>
 * ["测试设备", "CN", "beta", "5G设备"]
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
@Slf4j
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonbListStringTypeHandler extends BaseTypeHandler<List<String>> {

    private static final String JSON_NULL_SUMMARY = "null";

    /**
     * Jackson ObjectMapper，线程安全
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * List<String> 的类型引用，用于反序列化
     */
    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<List<String>>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            log.error("List<String> 序列化失败: message={}, size={}", e.getMessage(), summarizeListSize(parameter), e);
            throw new SQLException("Failed to serialize List<String> to JSON string", e);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    /**
     * 将 JSON 字符串解析为 List&lt;String&gt;
     *
     * @param json JSON 字符串
     * @return List&lt;String&gt; 对象，如果 json 为空则返回 null
     * @throws SQLException 解析失败时抛出
     */
    private List<String> parseJson(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, LIST_STRING_TYPE);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: message={}, summary={}", e.getMessage(), summarizeJson(json), e);
            throw new SQLException("Failed to deserialize JSON string to List<String>", e);
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

    /**
     * 获取列表大小（用于日志，避免泄露数据）
     *
     * @param list 列表
     * @return 列表大小，如果为 null 则返回 -1
     */
    private int summarizeListSize(List<String> list) {
        return list == null ? -1 : list.size();
    }
}
