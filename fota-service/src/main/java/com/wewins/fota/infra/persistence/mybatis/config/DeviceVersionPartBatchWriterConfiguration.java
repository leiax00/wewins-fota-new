package com.wewins.fota.infra.persistence.mybatis.config;

import com.wewins.fota.infra.persistence.mybatis.writer.DeviceVersionPartBatchWriter;
import com.wewins.fota.infra.persistence.mybatis.writer.MySqlDeviceVersionPartBatchWriter;
import com.wewins.fota.infra.persistence.mybatis.writer.PostgresDeviceVersionPartBatchWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class DeviceVersionPartBatchWriterConfiguration {

    @Bean
    public DeviceVersionPartBatchWriter deviceVersionPartBatchWriter(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null && productName.toLowerCase().contains("postgres")) {
                return new PostgresDeviceVersionPartBatchWriter(jdbcTemplate);
            }
            return new MySqlDeviceVersionPartBatchWriter(jdbcTemplate);
        } catch (Exception e) {
            throw new IllegalStateException("识别数据库类型失败", e);
        }
    }
}
