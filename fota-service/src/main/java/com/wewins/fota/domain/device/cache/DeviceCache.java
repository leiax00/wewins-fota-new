package com.wewins.fota.domain.device.cache;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCache implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long deviceId;

    private Long productId;

    private Long currentVersionId;

    private JsonNode tags;

    private Long importBatchId;

    private LocalDateTime cachedAt;
}
