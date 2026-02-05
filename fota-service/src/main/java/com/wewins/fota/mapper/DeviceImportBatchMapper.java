package com.wewins.fota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.entity.DeviceImportBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备导入批次 Mapper 接口
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Mapper
public interface DeviceImportBatchMapper extends BaseMapper<DeviceImportBatch> {
}
