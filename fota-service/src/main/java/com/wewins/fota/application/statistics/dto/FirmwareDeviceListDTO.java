package com.wewins.fota.application.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmwareDeviceListDTO {

    private List<DeviceItem> devices;

    private Long nextCursor;

    private LocalDateTime dataCalculatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceItem {

        private Long id;

        private String imei;

        private String status;

        private Long productId;

        private LocalDateTime firstSeenAt;

        private LocalDateTime lastSeenAt;

        private String version;

        private String internalVersion;
    }
}
