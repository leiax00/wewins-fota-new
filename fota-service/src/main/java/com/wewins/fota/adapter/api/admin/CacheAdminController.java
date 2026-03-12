package com.wewins.fota.adapter.api.admin;

import com.wewins.fota.adapter.api.admin.dto.CacheEvictRequestDTO;
import com.wewins.fota.adapter.api.admin.dto.CacheEvictPreviewDTO;
import com.wewins.fota.adapter.api.admin.dto.CacheEvictResultDTO;
import com.wewins.fota.adapter.api.admin.dto.RedisInfoDTO;
import com.wewins.fota.adapter.api.admin.dto.RedisKeyDetailDTO;
import com.wewins.fota.adapter.api.admin.dto.RedisKeyListDTO;
import com.wewins.fota.application.cache.CacheAdminService;
import com.wewins.fota.application.cache.RedisMonitorService;
import com.wewins.fota.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final CacheAdminService cacheAdminService;
    private final RedisMonitorService redisMonitorService;

    @GetMapping("/info")
    @PreAuthorize("@rbac.has('monitor:cache:read')")
    public ApiResponse<RedisInfoDTO> getRedisInfo() {
        return ApiResponse.success(redisMonitorService.getRedisInfo());
    }

    @GetMapping("/keys")
    @PreAuthorize("@rbac.has('monitor:cache:read')")
    public ApiResponse<RedisKeyListDTO> listKeys(
            @RequestParam(value = "pattern", required = false) String pattern,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", defaultValue = "100") int pageSize) {
        return ApiResponse.success(redisMonitorService.listKeys(pattern, cursor, pageSize));
    }

    @GetMapping("/keys/{key}")
    @PreAuthorize("@rbac.has('monitor:cache:read')")
    public ApiResponse<RedisKeyDetailDTO> getKeyDetail(@PathVariable String key) {
        return ApiResponse.success(redisMonitorService.getKeyDetail(key));
    }

    @PutMapping("/keys/{key}")
    @PreAuthorize("@rbac.has('monitor:cache:update')")
    public ApiResponse<Boolean> setKeyValue(
            @PathVariable String key,
            @RequestBody Map<String, Object> body) {
        String value = body.get("value") != null ? body.get("value").toString() : null;
        Long ttl = body.get("ttl") != null ? Long.parseLong(body.get("ttl").toString()) : null;
        return ApiResponse.success(redisMonitorService.setKeyValue(key, value, ttl));
    }

    @DeleteMapping("/keys/{key}")
    @PreAuthorize("@rbac.has('monitor:cache:update')")
    public ApiResponse<Boolean> deleteKey(@PathVariable String key) {
        return ApiResponse.success(redisMonitorService.deleteKey(key));
    }

    @PutMapping("/keys/{key}/ttl")
    @PreAuthorize("@rbac.has('monitor:cache:update')")
    public ApiResponse<Boolean> setKeyTtl(
            @PathVariable String key,
            @RequestParam("ttl") long ttlSeconds) {
        return ApiResponse.success(redisMonitorService.setTtl(key, ttlSeconds));
    }

    @PostMapping("/evict")
    @PreAuthorize("@rbac.has('monitor:cache:update')")
    public ApiResponse<CacheEvictResultDTO> evict(@RequestBody CacheEvictRequestDTO request) {
        return ApiResponse.success(cacheAdminService.evict(request));
    }

    @PostMapping("/evict/preview")
    @PreAuthorize("@rbac.has('monitor:cache:update')")
    public ApiResponse<CacheEvictPreviewDTO> previewEvict(@RequestBody CacheEvictRequestDTO request) {
        return ApiResponse.success(cacheAdminService.previewEvict(request));
    }
}
