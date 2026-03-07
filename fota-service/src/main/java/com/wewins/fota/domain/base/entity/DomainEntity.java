package com.wewins.fota.domain.base.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DomainEntity {

    private Long id;

    private LocalDateTime createdAt;

    private Long createdBy;

    private LocalDateTime updatedAt;

    private Long updatedBy;
}
