package com.wewins.fota.infra.persistence.mybatis.writer;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;

import java.util.List;

public interface DeviceVersionPartBatchWriter {

    void upsertCurrentVersionParts(List<DeviceInfoUpdateMessage> messages);

    void insertInitialVersionParts(List<DeviceInfoUpdateMessage> messages);


}
