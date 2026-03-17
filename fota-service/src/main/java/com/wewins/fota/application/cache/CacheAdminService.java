package com.wewins.fota.application.cache;

import com.wewins.fota.adapter.api.admin.dto.CacheEvictRequestDTO;
import com.wewins.fota.adapter.api.admin.dto.CacheEvictPreviewDTO;
import com.wewins.fota.adapter.api.admin.dto.CacheEvictResultDTO;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.policy.repository.PolicyCacheRepository;
import com.wewins.fota.domain.product.repository.ProductCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheAdminService {

    private final ProductCacheRepository productCacheRepository;
    private final PolicyCacheRepository policyCacheRepository;
    private final DeviceCacheRepository deviceCacheRepository;

    public CacheEvictPreviewDTO previewEvict(CacheEvictRequestDTO request) {
        List<String> targets = new ArrayList<>();

        if (request.getProductId() != null) {
            if (request.isEvictProductCache()) {
                targets.add("product-cache:productId=" + request.getProductId());
            }
            if (request.isEvictPolicyCache()) {
                targets.add("policy-cache:productId=" + request.getProductId());
            }
        }

        if (request.isEvictProductCache() && request.getProductModel() != null && !request.getProductModel().isBlank()) {
            targets.add("product-cache:model=" + request.getProductModel().trim());
        }

        List<String> imeis = normalizeImeis(request.getImeis());
        if (!imeis.isEmpty()) {
            targets.add("device-cache:imeis=" + imeis.size());
        }

        int scopes = targets.size();
        CacheEvictPreviewDTO preview = CacheEvictPreviewDTO.builder()
                .estimatedScopes(scopes)
                .estimatedDeviceCount(imeis.size())
                .affectedTargets(List.copyOf(targets))
                .executable(scopes > 0)
                .message(scopes > 0 ? "Cache eviction preview ready" : "No cache target selected")
                .build();
        audit("preview-evict", request, "scopes=" + scopes + ",devices=" + imeis.size());
        return preview;
    }

    public CacheEvictResultDTO evict(CacheEvictRequestDTO request) {
        int scopes = 0;
        int deviceCount = 0;

        if (request.getProductId() != null) {
            if (request.isEvictProductCache()) {
                productCacheRepository.evict(request.getProductId());
                scopes++;
            }
            if (request.isEvictPolicyCache()) {
                policyCacheRepository.evictProductPolicies(request.getProductId());
                scopes++;
            }
        }

        if (request.isEvictProductCache() && request.getProductModel() != null && !request.getProductModel().isBlank()) {
            productCacheRepository.evictByModel(request.getProductModel().trim());
            scopes++;
        }

        List<String> imeis = normalizeImeis(request.getImeis());
        if (!imeis.isEmpty()) {
            deviceCacheRepository.evictBatch(imeis);
            scopes++;
            deviceCount = imeis.size();
        }

        CacheEvictResultDTO result = CacheEvictResultDTO.builder()
                .evictedScopes(scopes)
                .evictedDeviceCount(deviceCount)
                .message(scopes > 0 ? "Cache eviction completed" : "No cache target selected")
                .build();
        audit("evict", request, "scopes=" + scopes + ",devices=" + deviceCount);
        return result;
    }

    private void audit(String action, CacheEvictRequestDTO request, String result) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String operator = authentication == null ? "anonymous" : authentication.getName();
        log.info("cache-audit action={} operator={} productId={} productModel={} imeiCount={} flags=product:{},policy:{} result={}",
                action,
                operator,
                request.getProductId(),
                request.getProductModel(),
                request.getImeis() == null ? 0 : request.getImeis().size(),
                request.isEvictProductCache(),
                request.isEvictPolicyCache(),
                result);
    }

    private List<String> normalizeImeis(List<String> imeis) {
        if (imeis == null || imeis.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String imei : imeis) {
            if (imei == null) {
                continue;
            }
            String normalized = imei.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }
}
