package com.wewins.fota.security.internal;

import lombok.Data;

/**
 * Pending rotate key payload.
 */
@Data
public class RegionRotateKey {
    private String keyId;
    private String secret;
    private long issuedAt;
}
