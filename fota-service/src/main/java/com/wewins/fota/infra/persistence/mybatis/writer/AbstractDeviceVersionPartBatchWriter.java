package com.wewins.fota.infra.persistence.mybatis.writer;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

abstract class AbstractDeviceVersionPartBatchWriter implements DeviceVersionPartBatchWriter {

    private static final int BATCH_SIZE = 500;

    protected final JdbcTemplate jdbcTemplate;

    protected AbstractDeviceVersionPartBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertCurrentVersionParts(List<DeviceInfoUpdateMessage> messages) {
        if (!hasPartUpdates(messages, false)) {
            return;
        }
        executeBatch(currentVersionSql(), messages, false);
    }

    @Override
    public void insertInitialVersionParts(List<DeviceInfoUpdateMessage> messages) {
        if (!hasPartUpdates(messages, true)) {
            return;
        }
        executeBatch(initialVersionSql(), messages, true);
    }

    protected abstract String currentVersionSql();

    protected abstract String initialVersionSql();

    private void executeBatch(String sql, List<DeviceInfoUpdateMessage> messages, boolean initial) {
        jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) ps -> {
            int batchCount = 0;
            for (DeviceInfoUpdateMessage message : messages) {
                Map<String, DeviceVersionPart> parts =
                        initial ? message.getInitialVersionParts() : message.getCurrentVersionParts();
                if (message.getDeviceId() == null || message.getAccessTime() == null || parts == null || parts.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, DeviceVersionPart> entry : parts.entrySet()) {
                    DeviceVersionPart part = entry.getValue();
                    if (!StringUtils.hasText(entry.getKey()) || part == null || part.getVersionId() == null) {
                        continue;
                    }
                    ps.setLong(1, message.getDeviceId());
                    ps.setString(2, entry.getKey());
                    ps.setLong(3, part.getVersionId());
                    ps.setString(4, firstNonBlank(part.getVersion(), "UNKNOWN", "UNKNOWN"));
                    ps.setString(5, part.getInternalVersion());
                    ps.setInt(6, "main".equals(entry.getKey()) ? 1 : 0);
                    ps.setObject(7, message.getAccessTime());
                    ps.addBatch();
                    batchCount++;
                    if (batchCount % BATCH_SIZE == 0) {
                        ps.executeBatch();
                    }
                }
            }
            if (batchCount % BATCH_SIZE != 0) {
                ps.executeBatch();
            }
            return null;
        });
    }

    private boolean hasPartUpdates(List<DeviceInfoUpdateMessage> messages, boolean initial) {
        for (DeviceInfoUpdateMessage message : messages) {
            Map<String, DeviceVersionPart> parts =
                    initial ? message.getInitialVersionParts() : message.getCurrentVersionParts();
            if (parts == null || parts.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, DeviceVersionPart> entry : parts.entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || entry.getValue().getVersionId() == null) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }
}
