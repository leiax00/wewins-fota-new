package com.wewins.fota.application.load.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadCapacitySourceConfig {

    private String type;
    private String resource;
    private String aggregation;
}
