package com.wewins.fota.infra.persistence.converter;

import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionPO;
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
public interface FirmwareVersionConverter {

    @Mapping(target = "tags", source = "tags", qualifiedByName = "toStringMap")
    @Mapping(target = "meta", source = "meta", qualifiedByName = "toTagMap")
    FirmwareVersion toDomain(FirmwareVersionPO po);

    List<FirmwareVersion> toDomainList(List<FirmwareVersionPO> poList);

    @Mapping(target = "tags", source = "tags", qualifiedByName = "toStringMapString")
    @Mapping(target = "meta", source = "meta", qualifiedByName = "toTagMapString")
    FirmwareVersionPO toPo(FirmwareVersion domain);
}
