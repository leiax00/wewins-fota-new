package com.wewins.fota.infra.persistence.mybatis.mapper.statistics;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.StatVersionDeviceCountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
@PrimaryDbMapper
public interface StatVersionDeviceCountMapper extends BaseMapper<StatVersionDeviceCountPO> {

    int upsert(StatVersionDeviceCountPO po);

    List<Map<String, Object>> getProductVersionTrend(@Param("productId") Long productId,
                                                     @Param("hours") int hours);

    List<Map<String, Object>> getAllVersionDeviceCounts();
}
