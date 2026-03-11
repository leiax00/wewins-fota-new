package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlStateDTO {

    private String region;

    private int loadScore;

    private String loadLevel;

    private double recommendedMultiplier;
}
