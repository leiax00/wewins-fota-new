package com.wewins.fota.infra.persistence.converter;

import com.wewins.fota.domain.device.model.entity.DeviceImportBatch;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceImportBatchPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        builder = @Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeviceImportBatchConverter {

    DeviceImportBatch toDomain(DeviceImportBatchPO po);

    List<DeviceImportBatch> toDomainList(List<DeviceImportBatchPO> poList);

    DeviceImportBatchPO toPo(DeviceImportBatch domain);
}
