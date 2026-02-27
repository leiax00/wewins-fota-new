package com.wewins.fota.infra.persistence.mybatis.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import com.wewins.fota.infra.persistence.mybatis.device.mapper.DeviceImportBatchMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 设备导入批次仓储实现
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Repository
@RequiredArgsConstructor
public class DeviceImportBatchRepositoryImpl implements DeviceImportBatchRepository {

    private final DeviceImportBatchMapper deviceImportBatchMapper;

    @Override
    public Optional<DeviceImportBatch> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        DeviceImportBatch batch = deviceImportBatchMapper.selectById(id);
        return Optional.ofNullable(batch);
    }

    @Override
    public Page<DeviceImportBatch> pageBatches(Page<DeviceImportBatch> page, String batchName, String status) {
        LambdaQueryWrapper<DeviceImportBatch> queryWrapper = new LambdaQueryWrapper<>();

        // 批次名称模糊查询
        if (StringUtils.isNotBlank(batchName)) {
            queryWrapper.like(DeviceImportBatch::getBatchName, batchName.trim());
        }

        // 状态精确查询
        if (StringUtils.isNotBlank(status)) {
            queryWrapper.eq(DeviceImportBatch::getStatus, status.trim().toUpperCase());
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(DeviceImportBatch::getCreatedAt);

        return deviceImportBatchMapper.selectPage(page, queryWrapper);
    }

    @Override
    public void create(DeviceImportBatch batch) {
        deviceImportBatchMapper.insert(batch);
    }

    @Override
    public void updateById(DeviceImportBatch batch) {
        deviceImportBatchMapper.updateById(batch);
    }

    @Override
    public List<DeviceImportBatch> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return deviceImportBatchMapper.selectList(
                new LambdaQueryWrapper<DeviceImportBatch>()
                        .in(DeviceImportBatch::getId, ids)
                        .orderByDesc(DeviceImportBatch::getCreatedAt)
        );
    }
}
