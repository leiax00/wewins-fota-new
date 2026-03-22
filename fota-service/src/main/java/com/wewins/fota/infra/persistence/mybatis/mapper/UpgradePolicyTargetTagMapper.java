package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyTargetTagPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface UpgradePolicyTargetTagMapper {

    @Select({
            "<script>",
            "SELECT id, policy_id, tag_key, tag_value, operator, created_at",
            "FROM upgrade_policy_target_tags",
            "WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<UpgradePolicyTargetTagPO> selectByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Delete({
            "<script>",
            "DELETE FROM upgrade_policy_target_tags WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Insert({
            "<script>",
            "INSERT INTO upgrade_policy_target_tags (policy_id, tag_key, tag_value, operator) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.policyId}, #{item.tagKey}, #{item.tagValue}, #{item.operator})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<UpgradePolicyTargetTagPO> items);
}
