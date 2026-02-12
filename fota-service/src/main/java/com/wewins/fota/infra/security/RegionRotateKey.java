package com.wewins.fota.infra.security;

import lombok.Data;

/**
 * 分区密钥轮换载荷
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Data
public class RegionRotateKey {
    private String keyId;
    private String secret;
    private long issuedAt;
}
