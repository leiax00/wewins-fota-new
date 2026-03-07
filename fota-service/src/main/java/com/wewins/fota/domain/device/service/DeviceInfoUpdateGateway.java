package com.wewins.fota.domain.device.service;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;

public interface DeviceInfoUpdateGateway {
    void accept(DeviceInfoUpdateMessage message);
}
