package com.wewins.fota.infra.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProductChangedEvent extends ApplicationEvent {

    private final Long productId;
    private final ChangeType changeType;
    private final String oldModel;
    private final String newModel;

    public ProductChangedEvent(Object source, Long productId, ChangeType changeType) {
        this(source, productId, changeType, null, null);
    }

    public ProductChangedEvent(Object source,
                               Long productId,
                               ChangeType changeType,
                               String oldModel,
                               String newModel) {
        super(source);
        this.productId = productId;
        this.changeType = changeType;
        this.oldModel = oldModel;
        this.newModel = newModel;
    }

}
