package com.wewins.fota.infra.persistence.mybatis.mapper.statistics;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
@PrimaryDbMapper
public interface StatisticsMapper {

    List<Map<String, Object>> getProductVersionDistribution(Long productId);

    Map<String, Object> getPolicyAffectedAndUpgraded(Long policyId);

    Long getPolicyUpgradedTotal(Long policyId);

    List<Map<String, Object>> getFirmwareDevices(@Param("versionId") Long versionId,
                                                 @Param("cursor") Long cursor,
                                                 @Param("size") int size,
                                                 @Param("keyword") String keyword);

    Long getFirmwareDeviceCount(Long versionId);

    List<Map<String, Object>> getProductDeviceList(@Param("productId") Long productId,
                                                   @Param("cursor") Long cursor,
                                                   @Param("size") int size,
                                                   @Param("keyword") String keyword);
}
