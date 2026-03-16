package com.wewins.fota.application.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.audit.dto.OperationLogPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.audit.model.entity.OperationLog;
import com.wewins.fota.infra.persistence.mybatis.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogAppService {

    private final OperationLogMapper operationLogMapper;

    public void create(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    public Page<OperationLog> page(OperationLogPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new OperationLogPageReqDTO();
        }
        reqDTO.validate();
        return operationLogMapper.selectPage(
                new Page<>(reqDTO.getPage(), reqDTO.getSize()),
                reqDTO.toWrapper()
        );
    }

    public OperationLog getById(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        OperationLog operationLog = operationLogMapper.selectById(id);
        if (operationLog == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return operationLog;
    }
}
