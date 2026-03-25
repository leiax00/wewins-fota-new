package com.wewins.fota.domain.device.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCache implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long deviceId;

    private Long productId;

    private Map<String, String> tags;

    private Long importBatchId;

    private DeviceVersionParts versionParts;

    private LocalDateTime cachedAt;
}
