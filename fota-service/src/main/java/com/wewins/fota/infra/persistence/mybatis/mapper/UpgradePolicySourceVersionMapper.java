package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicySourceVersionPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface UpgradePolicySourceVersionMapper {

    @Select({
            "<script>",
            "SELECT policy_id, source_version_id, created_at",
            "FROM upgrade_policy_source_versions",
            "WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<UpgradePolicySourceVersionPO> selectByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Delete({
            "<script>",
            "DELETE FROM upgrade_policy_source_versions WHERE policy_id IN",
            "<foreach collection='policyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Insert({
            "<script>",
            "INSERT INTO upgrade_policy_source_versions (policy_id, source_version_id) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.policyId}, #{item.sourceVersionId})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<UpgradePolicySourceVersionPO> items);
}
