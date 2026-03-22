package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyTargetDevicePO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface UpgradePolicyTargetDeviceMapper {

    @Select({
            "<script>",
            "SELECT policy_id, imei, created_at",
            "FROM upgrade_policy_target_devices",
            "WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<UpgradePolicyTargetDevicePO> selectByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Delete({
            "<script>",
            "DELETE FROM upgrade_policy_target_devices WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Insert({
            "<script>",
            "INSERT INTO upgrade_policy_target_devices (policy_id, imei) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.policyId}, #{item.imei})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<UpgradePolicyTargetDevicePO> items);
}
