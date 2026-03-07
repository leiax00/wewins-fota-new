package com.wewins.fota.domain.policy.model.vo;

import com.wewins.fota.domain.policy.model.enums.TimeWindowType;
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
public class PolicyTimeWindow implements Serializable {

    private static final long serialVersionUID = 1L;

    private TimeWindowType type;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
