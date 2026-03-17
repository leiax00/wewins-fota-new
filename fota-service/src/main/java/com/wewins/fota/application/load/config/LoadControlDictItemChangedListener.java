package com.wewins.fota.application.load.config;

import com.wewins.fota.module.system.application.event.DictItemChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadControlDictItemChangedListener {

    private final LoadControlDictionaryPublishService publishService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDictItemChanged(DictItemChangedEvent event) {
        if (!publishService.shouldPublish(event)) {
            return;
        }
        publishService.publishFromDictionary(event);
        log.info("Load-control snapshot publish triggered by dict item change: dictTypeCode={}, itemId={}",
                event.getDictTypeCode(), event.getItemId());
    }
}
