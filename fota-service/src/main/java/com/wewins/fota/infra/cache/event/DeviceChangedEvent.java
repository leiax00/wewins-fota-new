package com.wewins.fota.infra.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DeviceChangedEvent extends ApplicationEvent {

    private final String imei;
    private final ChangeType changeType;

    public DeviceChangedEvent(Object source, String imei, ChangeType changeType) {
        super(source);
        this.imei = imei;
        this.changeType = changeType;
    }

}
