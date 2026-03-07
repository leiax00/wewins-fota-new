package com.wewins.fota.infra.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class DeviceBatchChangedEvent extends ApplicationEvent {

    private final List<String> imeis;
    private final ChangeType changeType;

    public DeviceBatchChangedEvent(Object source, List<String> imeis, ChangeType changeType) {
        super(source);
        this.imeis = imeis;
        this.changeType = changeType;
    }
}
