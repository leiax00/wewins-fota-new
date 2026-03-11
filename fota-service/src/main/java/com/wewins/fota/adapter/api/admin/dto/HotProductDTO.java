package com.wewins.fota.adapter.api.admin.dto;

import com.wewins.fota.domain.load.model.enums.ProductPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotProductDTO {

    private String product;

    private double checkQps;

    private double reportQps;

    private double trafficShare;

    private ProductPriority priority;

    private double intervalBias;

    private boolean hotspotProtectionEnabled;
}
