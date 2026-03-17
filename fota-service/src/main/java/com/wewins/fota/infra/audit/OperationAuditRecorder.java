package com.wewins.fota.infra.audit;

import com.wewins.fota.application.audit.OperationLogAppService;
import com.wewins.fota.domain.audit.model.entity.OperationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationAuditRecorder {

    private final OperationLogAppService operationLogAppService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(OperationLog operationLog) {
        operationLogAppService.create(operationLog);
    }

    public void recordQuietly(OperationLog operationLog) {
        try {
            record(operationLog);
        } catch (Exception e) {
            log.warn("写入操作日志失败: actionCode={}, requestPath={}",
                    operationLog.getActionCode(), operationLog.getRequestPath(), e);
        }
    }
}
