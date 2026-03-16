package com.wewins.fota.application.load.config;

import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadControlRuntimeConfig {

    private Long version;
    private Instant updatedAt;
    private String updatedBy;
    private ControlParameter control;
    private LoadScoringConfig scoring;
    private SentinelRulesDTO sentinel;
}
