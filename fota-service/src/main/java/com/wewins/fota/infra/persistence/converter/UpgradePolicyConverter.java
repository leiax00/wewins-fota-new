package com.wewins.fota.infra.persistence.converter;

import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        builder = @Builder(disableBuilder = true),
        uses = JsonMapper.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UpgradePolicyConverter {

    @Mapping(target = "sourceVersions", source = "sourceVersions", qualifiedByName = "toLongSet")
    @Mapping(target = "targetImeis", source = "targetImeis", qualifiedByName = "toStringSet")
    @Mapping(target = "targetDeviceBatchIds", source = "targetDeviceBatchIds", qualifiedByName = "toLongSet")
    @Mapping(target = "targetDeviceTags", source = "targetDeviceTags", qualifiedByName = "toTagMap")
    @Mapping(target = "timeWindow", source = "timeWindow", qualifiedByName = "toPolicyTimeWindow")
    UpgradePolicy toDomain(UpgradePolicyPO po);

    List<UpgradePolicy> toDomainList(List<UpgradePolicyPO> poList);

    @Mapping(target = "sourceVersions", source = "sourceVersions", qualifiedByName = "toLongSetString")
    @Mapping(target = "targetImeis", source = "targetImeis", qualifiedByName = "toStringSetString")
    @Mapping(target = "targetDeviceBatchIds", source = "targetDeviceBatchIds", qualifiedByName = "toLongSetString")
    @Mapping(target = "targetDeviceTags", source = "targetDeviceTags", qualifiedByName = "toTagMapString")
    @Mapping(target = "timeWindow", source = "timeWindow", qualifiedByName = "toPolicyTimeWindowString")
    UpgradePolicyPO toPo(UpgradePolicy domain);
}
