package com.wewins.fota.infra.persistence.mybatis.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.domain.device.value.DeviceVersionParts;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;

public class DeviceVersionPartsTypeHandler extends BaseTypeHandler<DeviceVersionParts> {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, DeviceVersionParts parameter, JdbcType jdbcType) 
            throws SQLException {
        try {
            String jsonString = OBJECT_MAPPER.writeValueAsString(parameter);
            ps.setObject(i, jsonString, Types.OTHER);
        } catch (Exception e) {
            throw new SQLException("Failed to serialize DeviceVersionParts to JSONB", e);
        }
    }
    
    @Override
    public DeviceVersionParts getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseDeviceVersionParts(json);
    }
    
    @Override
    public DeviceVersionParts getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseDeviceVersionParts(json);
    }
    
    @Override
    public DeviceVersionParts getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseDeviceVersionParts(json);
    }
    
    private DeviceVersionParts parseDeviceVersionParts(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return DeviceVersionParts.builder().build();
        }
        try {
            return OBJECT_MAPPER.readValue(json, DeviceVersionParts.class);
        } catch (Exception e) {
            throw new SQLException("Failed to parse DeviceVersionParts from JSONB", e);
        }
    }
}
