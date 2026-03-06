package com.wewins.fota.domain.device.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.domain.device.value.DeviceVersionParts;
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

    private JsonNode tags;

    private Long importBatchId;

    private DeviceVersionParts versionParts;

    private DeviceVersionParts initialVersionParts;

    private LocalDateTime firstSeenAt;

    private LocalDateTime cachedAt;
}
