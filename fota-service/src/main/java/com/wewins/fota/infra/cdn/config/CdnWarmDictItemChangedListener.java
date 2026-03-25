package com.wewins.fota.infra.cdn.config;

import com.wewins.fota.module.system.application.event.DictItemChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdnWarmDictItemChangedListener {

    private final CdnWarmDictionaryConfigProvider configProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDictItemChanged(DictItemChangedEvent event) {
        if (event == null || event.getDictTypeCode() == null) {
            return;
        }
        if (!CdnWarmDictionaryConfigProvider.TYPE_CDN_WARM_WORKER_CONFIG.equals(event.getDictTypeCode())) {
            return;
        }
        configProvider.evictCache();
        log.info("Evicted CDN warm config cache after dict change: dictTypeCode={}, itemId={}",
                event.getDictTypeCode(), event.getItemId());
    }
}
