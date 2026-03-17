package com.wewins.fota.adapter.api.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class CacheEvictRequestDTO {

    private Long productId;

    private String productModel;

    private List<String> imeis;

    private boolean evictProductCache = true;

    private boolean evictPolicyCache = true;
}
