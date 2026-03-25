package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyTargetBatchPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface UpgradePolicyTargetBatchMapper {

    @Select({
            "<script>",
            "SELECT policy_id, batch_id, created_at",
            "FROM upgrade_policy_target_batches",
            "WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<UpgradePolicyTargetBatchPO> selectByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Delete({
            "<script>",
            "DELETE FROM upgrade_policy_target_batches WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Insert({
            "<script>",
            "INSERT INTO upgrade_policy_target_batches (policy_id, batch_id) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.policyId}, #{item.batchId})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<UpgradePolicyTargetBatchPO> items);
}
