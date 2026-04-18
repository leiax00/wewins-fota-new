package com.wewins.fota.application.statistics;

import com.wewins.fota.application.statistics.dto.PolicySummaryDTO;
import com.wewins.fota.application.statistics.dto.StatisticsResponseDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.infra.persistence.mybatis.mapper.statistics.StatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyStatisticsService {

    private final StatisticsMapper statisticsMapper;

    public StatisticsResponseDTO<PolicySummaryDTO> getSummary(Long policyId) {
        if (policyId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "策略 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("查询策略统计汇总: policyId={}", policyId);
        }

        Map<String, Object> policySummary = statisticsMapper.getPolicyAffectedAndUpgraded(policyId);
        long affectedTotal = asLong(policySummary == null ? null : policySummary.get("affectedTotal"), 0L);
        long upgradedTotal = asLong(policySummary == null ? null : policySummary.get("upgradedTotal"), 0L);
        long pendingUpgradeTotal = Math.max(affectedTotal - upgradedTotal, 0L);
        LocalDateTime dataCalculatedAt = LocalDateTime.now();

        PolicySummaryDTO data = PolicySummaryDTO.builder()
                .affectedTotal(affectedTotal)
                .upgradedTotal(upgradedTotal)
                .pendingUpgradeTotal(pendingUpgradeTotal)
                .dataCalculatedAt(dataCalculatedAt)
                .build();

        return StatisticsResponseDTO.<PolicySummaryDTO>builder()
                .data(data)
                .dataCalculatedAt(dataCalculatedAt)
                .scope("policy")
                .scopeId(policyId)
                .build();
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

}
