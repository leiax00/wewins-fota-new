package com.wewins.fota.infra.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FirmwareChangedEvent extends ApplicationEvent {

    private final Long productId;
    private final Long versionId;
    private final String tag;
    private final ChangeType changeType;

    public FirmwareChangedEvent(Object source, Long productId, Long versionId, String tag, ChangeType changeType) {
        super(source);
        this.productId = productId;
        this.versionId = versionId;
        this.tag = tag;
        this.changeType = changeType;
    }

}
