package com.wewins.fota.infra.persistence.converter;

import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.infra.persistence.mybatis.po.DevicePO;
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
public interface DeviceConverter {

    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "versionParts", ignore = true)
    @Mapping(target = "initialVersionParts", ignore = true)
    Device toDomain(DevicePO po);

    List<Device> toDomainList(List<DevicePO> poList);

    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "versionParts", ignore = true)
    @Mapping(target = "initialVersionParts", ignore = true)
    DevicePO toPo(Device domain);
}
