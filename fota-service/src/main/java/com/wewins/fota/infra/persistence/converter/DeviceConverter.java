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

    @Mapping(target = "tags", source = "tags", qualifiedByName = "toStringMap")
    @Mapping(target = "versionParts", source = "versionParts", qualifiedByName = "toDeviceVersionParts")
    @Mapping(target = "initialVersionParts", source = "initialVersionParts", qualifiedByName = "toDeviceVersionParts")
    Device toDomain(DevicePO po);

    List<Device> toDomainList(List<DevicePO> poList);

    @Mapping(target = "tags", source = "tags", qualifiedByName = "toStringMapString")
    @Mapping(target = "versionParts", source = "versionParts", qualifiedByName = "toDeviceVersionPartsString")
    @Mapping(target = "initialVersionParts", source = "initialVersionParts", qualifiedByName = "toDeviceVersionPartsString")
    DevicePO toPo(Device domain);
}
