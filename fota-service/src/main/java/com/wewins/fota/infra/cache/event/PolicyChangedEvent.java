package com.wewins.fota.infra.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PolicyChangedEvent extends ApplicationEvent {

    private final Long productId;
    private final Long policyId;
    private final ChangeType changeType;

    public PolicyChangedEvent(Object source, Long productId, Long policyId, ChangeType changeType) {
        super(source);
        this.productId = productId;
        this.policyId = policyId;
        this.changeType = changeType;
    }

}
