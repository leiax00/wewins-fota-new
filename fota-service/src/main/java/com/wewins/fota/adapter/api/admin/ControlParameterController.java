package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.ControlParameterDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/admin/control")
@RequiredArgsConstructor
public class ControlParameterController {

    private final ControlParameterRepository repository;

    @GetMapping("/global")
    public ApiResponse<ControlParameterDTO> getGlobalConfig() {
        ControlParameter param = repository.getGlobal().orElse(ControlParameter.createGlobalDefault());
        return ApiResponse.success(toDTO(param));
    }

    @PutMapping("/global")
    public ApiResponse<Void> updateGlobalConfig(
            @RequestBody ControlParameterDTO dto,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        String operatorName = operator != null ? operator : "system";
        ControlParameter param = ControlParameter.builder()
                .protectedCheckIntervalSeconds(dto.getProtectedCheckIntervalSeconds())
                .checkIntervalMultiplier(dto.getCheckIntervalMultiplier())
                .protectedIntervalMultiplier(dto.getProtectedIntervalMultiplier())
                .downloadDelayMultiplier(dto.getDownloadDelayMultiplier())
                .intervalBias(dto.getIntervalBias())
                .minCheckIntervalSeconds(dto.getMinCheckIntervalSeconds())
                .maxCheckIntervalSeconds(dto.getMaxCheckIntervalSeconds())
                .priority(dto.getPriority())
                .hotspotProtectionEnabled(dto.getHotspotProtectionEnabled())
                .forceMaintenance(dto.getForceMaintenance())
                .maintenanceMessage(dto.getMaintenanceMessage())
                .updatedAt(Instant.now())
                .updatedBy(operatorName)
                .build();
        repository.saveGlobal(param);
        log.info("Global control parameter updated by {}", operatorName);
        return ApiResponse.success();
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<ControlParameterDTO> getProductConfig(@PathVariable Long productId) {
        ControlParameter param = repository.getByProduct(productId)
                .orElse(ControlParameter.createProductDefault(productId));
        return ApiResponse.success(toDTO(param));
    }

    @PutMapping("/product/{productId}")
    public ApiResponse<Void> updateProductConfig(
            @PathVariable Long productId,
            @RequestBody ControlParameterDTO dto,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        String operatorName = operator != null ? operator : "system";
        ControlParameter param = ControlParameter.builder()
                .productId(productId)
                .protectedCheckIntervalSeconds(dto.getProtectedCheckIntervalSeconds())
                .checkIntervalMultiplier(dto.getCheckIntervalMultiplier())
                .protectedIntervalMultiplier(dto.getProtectedIntervalMultiplier())
                .downloadDelayMultiplier(dto.getDownloadDelayMultiplier())
                .intervalBias(dto.getIntervalBias())
                .minCheckIntervalSeconds(dto.getMinCheckIntervalSeconds())
                .maxCheckIntervalSeconds(dto.getMaxCheckIntervalSeconds())
                .priority(dto.getPriority())
                .hotspotProtectionEnabled(dto.getHotspotProtectionEnabled())
                .forceMaintenance(dto.getForceMaintenance())
                .maintenanceMessage(dto.getMaintenanceMessage())
                .updatedAt(Instant.now())
                .updatedBy(operatorName)
                .build();
        repository.saveByProduct(productId, param);
        log.info("Product {} control parameter updated by {}", productId, operatorName);
        return ApiResponse.success();
    }

    private ControlParameterDTO toDTO(ControlParameter param) {
        return ControlParameterDTO.builder()
                .productId(param.getProductId())
                .protectedCheckIntervalSeconds(param.getProtectedCheckIntervalSeconds())
                .checkIntervalMultiplier(param.getCheckIntervalMultiplier())
                .protectedIntervalMultiplier(param.getProtectedIntervalMultiplier())
                .downloadDelayMultiplier(param.getDownloadDelayMultiplier())
                .intervalBias(param.getIntervalBias())
                .minCheckIntervalSeconds(param.getMinCheckIntervalSeconds())
                .maxCheckIntervalSeconds(param.getMaxCheckIntervalSeconds())
                .priority(param.getPriority())
                .hotspotProtectionEnabled(param.getHotspotProtectionEnabled())
                .forceMaintenance(param.getForceMaintenance())
                .maintenanceMessage(param.getMaintenanceMessage())
                .updatedAt(param.getUpdatedAt())
                .updatedBy(param.getUpdatedBy())
                .build();
    }
}
