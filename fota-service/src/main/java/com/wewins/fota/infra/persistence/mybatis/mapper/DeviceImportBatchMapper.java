package com.wewins.fota.infra.persistence.mybatis.mapper;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceImportBatchPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备导入批次 Mapper 接口
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Mapper
@PrimaryDbMapper
public interface DeviceImportBatchMapper extends BaseMapper<DeviceImportBatchPO> {
}
