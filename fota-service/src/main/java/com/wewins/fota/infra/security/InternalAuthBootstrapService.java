package com.wewins.fota.infra.security;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.application.DictItemAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


/**
 * 内部鉴权密钥引导写入（仅首次）
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnAppMode("main")
@ConditionalOnProperty(prefix = "app.internal-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InternalAuthBootstrapService {

    private static final String BOOTSTRAP_DICT_TYPE_CODE = "region_bootstrap_secret";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DictItemAppService dictItemService;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapSecrets() {
        bootstrapFromDict();
    }

    private void trySetSecret(String regionCode, String secret) {
        if (regionCode == null || regionCode.isBlank() || secret == null || secret.isBlank()) {
            return;
        }
        String key = String.format(RedisKeyConstants.REGION_SECRET_KEY_TEMPLATE, regionCode);
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, secret);
        if (Boolean.TRUE.equals(success)) {
            log.info("写入初始分区密钥: regionCode={}", regionCode);
        }
    }

    private void bootstrapFromDict() {
        for (DictItem item : dictItemService.listItemsByTypeCode(BOOTSTRAP_DICT_TYPE_CODE)) {
            String regionCode = resolveRegionCode(item);
            String secret = resolveSecret(item);
            trySetSecret(regionCode, secret);
        }
    }

    private String resolveRegionCode(DictItem item) {
        if (item == null) {
            return null;
        }
        if (item.getExtra() != null && item.getExtra().hasNonNull("secret")) {
            return item.getValue();
        }
        return item.getLabel();
    }

    private String resolveSecret(DictItem item) {
        if (item == null) {
            return null;
        }
        if (item.getExtra() != null && item.getExtra().hasNonNull("secret")) {
            return item.getExtra().get("secret").asText();
        }
        return item.getValue();
    }
}
