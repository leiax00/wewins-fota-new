package com.wewins.fota.infra.persistence.mybatis.device.mapper;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.domain.device.entity.DeviceImportBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备导入批次 Mapper 接口
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Mapper
@PrimaryDbMapper
public interface DeviceImportBatchMapper extends BaseMapper<DeviceImportBatch> {
}
