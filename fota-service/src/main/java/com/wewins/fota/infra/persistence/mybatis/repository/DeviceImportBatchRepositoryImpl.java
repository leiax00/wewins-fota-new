package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.device.model.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import com.wewins.fota.infra.persistence.converter.DeviceImportBatchConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.DeviceImportBatchMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceImportBatchPO;
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

    private final DeviceImportBatchConverter deviceImportBatchConverter;

    @Override
    public Optional<DeviceImportBatch> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        DeviceImportBatch batch = deviceImportBatchConverter.toDomain(deviceImportBatchMapper.selectById(id));
        return Optional.ofNullable(batch);
    }

    @Override
    public Page<DeviceImportBatch> pageBatches(Page<DeviceImportBatch> page, String batchName, Long productId, String status) {
        LambdaQueryWrapper<DeviceImportBatchPO> queryWrapper = new LambdaQueryWrapper<>();

        // 批次名称模糊查询
        if (StringUtils.isNotBlank(batchName)) {
            queryWrapper.like(DeviceImportBatchPO::getBatchName, batchName.trim());
        }

        // 产品ID精确查询
        if (productId != null) {
            queryWrapper.eq(DeviceImportBatchPO::getProductId, productId);
        }

        // 状态精确查询
        if (StringUtils.isNotBlank(status)) {
            queryWrapper.eq(DeviceImportBatchPO::getStatus, status.trim().toUpperCase());
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(DeviceImportBatchPO::getCreatedAt);

        Page<DeviceImportBatchPO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<DeviceImportBatchPO> queried = deviceImportBatchMapper.selectPage(poPage, queryWrapper);
        Page<DeviceImportBatch> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(deviceImportBatchConverter.toDomainList(queried.getRecords()));
        return result;
    }

    @Override
    public void create(DeviceImportBatch batch) {
        deviceImportBatchMapper.insert(deviceImportBatchConverter.toPo(batch));
    }

    @Override
    public void updateById(DeviceImportBatch batch) {
        deviceImportBatchMapper.updateById(deviceImportBatchConverter.toPo(batch));
    }

    @Override
    public Optional<DeviceImportBatch> findByBatchNameAndProductId(String batchName, Long productId) {
        if (StringUtils.isBlank(batchName) || productId == null) {
            return Optional.empty();
        }

        DeviceImportBatchPO batchPo = deviceImportBatchMapper.selectOne(
                new LambdaQueryWrapper<DeviceImportBatchPO>()
                        .eq(DeviceImportBatchPO::getBatchName, batchName.trim())
                        .eq(DeviceImportBatchPO::getProductId, productId)
                        .orderByDesc(DeviceImportBatchPO::getCreatedAt)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(deviceImportBatchConverter.toDomain(batchPo));
    }

    @Override
    public List<DeviceImportBatch> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<DeviceImportBatchPO> batchPos = deviceImportBatchMapper.selectList(
                new LambdaQueryWrapper<DeviceImportBatchPO>()
                        .in(DeviceImportBatchPO::getId, ids)
                        .orderByDesc(DeviceImportBatchPO::getCreatedAt)
        );
        return deviceImportBatchConverter.toDomainList(batchPos);
    }
}
