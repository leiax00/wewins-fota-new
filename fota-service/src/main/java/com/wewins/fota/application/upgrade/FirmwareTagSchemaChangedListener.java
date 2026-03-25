package com.wewins.fota.application.upgrade;

import com.wewins.fota.module.system.application.event.DictItemChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirmwareTagSchemaChangedListener {

    private final FirmwareTagSchemaProvider firmwareTagSchemaProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDictItemChanged(DictItemChangedEvent event) {
        if (event == null || event.getDictTypeCode() == null) {
            return;
        }
        if (!FirmwareTagSchemaProvider.TYPE_FIRMWARE_TAGS.equals(event.getDictTypeCode())) {
            return;
        }
        firmwareTagSchemaProvider.evictCache();
        log.info("Evicted firmware tag schema cache after dict change: itemId={}", event.getItemId());
    }
}
