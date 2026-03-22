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

    @Mapping(target = "sourceVersions", ignore = true)
    @Mapping(target = "targetImeis", ignore = true)
    @Mapping(target = "targetDeviceBatchIds", ignore = true)
    @Mapping(target = "targetDeviceTags", ignore = true)
    @Mapping(target = "timeWindow", source = "timeWindow", qualifiedByName = "toPolicyTimeWindow")
    UpgradePolicy toDomain(UpgradePolicyPO po);

    List<UpgradePolicy> toDomainList(List<UpgradePolicyPO> poList);

    @Mapping(target = "sourceVersions", ignore = true)
    @Mapping(target = "targetImeis", ignore = true)
    @Mapping(target = "targetDeviceBatchIds", ignore = true)
    @Mapping(target = "targetDeviceTags", ignore = true)
    @Mapping(target = "timeWindow", source = "timeWindow", qualifiedByName = "toPolicyTimeWindowString")
    UpgradePolicyPO toPo(UpgradePolicy domain);
}
