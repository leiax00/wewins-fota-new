package com.wewins.fota.application.cache;

import com.wewins.fota.adapter.api.admin.dto.CacheEvictRequestDTO;
import com.wewins.fota.adapter.api.admin.dto.CacheEvictResultDTO;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.policy.repository.PolicyCacheRepository;
import com.wewins.fota.domain.product.repository.ProductCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CacheAdminService {

    private final ProductCacheRepository productCacheRepository;
    private final PolicyCacheRepository policyCacheRepository;
    private final DeviceCacheRepository deviceCacheRepository;

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

        if (request.getProductModel() != null && !request.getProductModel().isBlank()) {
            productCacheRepository.evictByModel(request.getProductModel().trim());
            scopes++;
        }

        List<String> imeis = normalizeImeis(request.getImeis());
        if (!imeis.isEmpty()) {
            deviceCacheRepository.evictBatch(imeis);
            scopes++;
            deviceCount = imeis.size();
        }

        return CacheEvictResultDTO.builder()
                .evictedScopes(scopes)
                .evictedDeviceCount(deviceCount)
                .message(scopes > 0 ? "Cache eviction completed" : "No cache target selected")
                .build();
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
