package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.CacheEvictRequestDTO;
import com.wewins.fota.adapter.api.admin.dto.CacheEvictResultDTO;
import com.wewins.fota.application.cache.CacheAdminService;
import com.wewins.fota.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final CacheAdminService cacheAdminService;

    @PostMapping("/evict")
    @PreAuthorize("@rbac.has('monitor:cache:update')")
    public ApiResponse<CacheEvictResultDTO> evict(@RequestBody CacheEvictRequestDTO request) {
        return ApiResponse.success(cacheAdminService.evict(request));
    }
}
