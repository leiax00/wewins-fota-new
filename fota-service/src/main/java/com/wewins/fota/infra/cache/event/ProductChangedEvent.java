package com.wewins.fota.infra.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProductChangedEvent extends ApplicationEvent {

    private final Long productId;
    private final ChangeType changeType;

    public ProductChangedEvent(Object source, Long productId, ChangeType changeType) {
        super(source);
        this.productId = productId;
        this.changeType = changeType;
    }

}
