package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.ControlParameterDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/admin/control")
@RequiredArgsConstructor
public class ControlParameterController {

    private final ControlParameterRepository repository;

    @GetMapping("/global")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<ControlParameterDTO> getGlobalConfig() {
        ControlParameter param = repository.getGlobal().orElse(ControlParameter.createGlobalDefault());
        return ApiResponse.success(toDTO(param));
    }

    @PutMapping("/global")
    @PreAuthorize("@rbac.has('monitor:load:update')")
    public ApiResponse<Void> updateGlobalConfig(
            @RequestBody ControlParameterDTO dto,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        String operatorName = operator != null ? operator : "system";
        ControlParameter param = ControlParameter.builder()
                .protectedIntervalMultiplier(dto.getProtectedIntervalMultiplier())
                .downloadDelayMultiplier(dto.getDownloadDelayMultiplier())
                .minCheckIntervalSeconds(dto.getMinCheckIntervalSeconds())
                .maxCheckIntervalSeconds(dto.getMaxCheckIntervalSeconds())
                .updatedAt(Instant.now())
                .updatedBy(operatorName)
                .build();
        repository.saveGlobal(param);
        ControlParameter saved = repository.getGlobal()
                .orElseThrow(() -> new IllegalStateException("Global control parameter was not persisted"));
        log.info("Global control parameter updated by {}", operatorName);
        log.debug("Saved global control parameter: {}", saved);
        return ApiResponse.success();
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("@rbac.has('monitor:load:read')")
    public ApiResponse<ControlParameterDTO> getProductConfig(@PathVariable Long productId) {
        ControlParameter param = repository.getByProduct(productId)
                .orElse(ControlParameter.createProductDefault(productId));
        return ApiResponse.success(toDTO(param));
    }

    @PutMapping("/product/{productId}")
    @PreAuthorize("@rbac.has('monitor:load:update')")
    public ApiResponse<Void> updateProductConfig(
            @PathVariable Long productId,
            @RequestBody ControlParameterDTO dto,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        String operatorName = operator != null ? operator : "system";
        ControlParameter param = ControlParameter.builder()
                .productId(productId)
                .protectedIntervalMultiplier(dto.getProtectedIntervalMultiplier())
                .downloadDelayMultiplier(dto.getDownloadDelayMultiplier())
                .minCheckIntervalSeconds(dto.getMinCheckIntervalSeconds())
                .maxCheckIntervalSeconds(dto.getMaxCheckIntervalSeconds())
                .updatedAt(Instant.now())
                .updatedBy(operatorName)
                .build();
        repository.saveByProduct(productId, param);
        ControlParameter saved = repository.getByProduct(productId)
                .orElseThrow(() -> new IllegalStateException("Product control parameter was not persisted"));
        log.info("Product {} control parameter updated by {}", productId, operatorName);
        log.debug("Saved product control parameter: productId={}, value={}", productId, saved);
        return ApiResponse.success();
    }

    private ControlParameterDTO toDTO(ControlParameter param) {
        return ControlParameterDTO.builder()
                .productId(param.getProductId())
                .protectedIntervalMultiplier(param.getProtectedIntervalMultiplier())
                .downloadDelayMultiplier(param.getDownloadDelayMultiplier())
                .minCheckIntervalSeconds(param.getMinCheckIntervalSeconds())
                .maxCheckIntervalSeconds(param.getMaxCheckIntervalSeconds())
                .updatedAt(param.getUpdatedAt())
                .updatedBy(param.getUpdatedBy())
                .build();
    }
}
