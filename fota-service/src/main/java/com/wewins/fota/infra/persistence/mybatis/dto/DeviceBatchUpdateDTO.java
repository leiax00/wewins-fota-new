package com.wewins.fota.infra.persistence.mybatis.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceBatchUpdateDTO {

    private Long id;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;
}
