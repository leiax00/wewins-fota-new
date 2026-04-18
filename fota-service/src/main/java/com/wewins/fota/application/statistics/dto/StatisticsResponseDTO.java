package com.wewins.fota.application.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponseDTO<T> {

    private T data;

    private LocalDateTime dataCalculatedAt;

    private String scope;

    private Long scopeId;
}
