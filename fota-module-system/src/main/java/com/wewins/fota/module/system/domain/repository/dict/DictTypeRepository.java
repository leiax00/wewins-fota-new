package com.wewins.fota.module.system.domain.repository.dict;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.dict.DictType;
import com.wewins.fota.module.system.dto.DictTypePageReqDTO;

public interface DictTypeRepository {

    void create(DictType dictType);

    boolean updateById(DictType dictType);

    boolean deleteById(Long dictTypeId);

    DictType findById(Long id);

    DictType findFirstByCode(String code);

    Page<DictType> page(DictTypePageReqDTO reqDTO);

    long countByCodeExcludingId(String code, Long excludeId);
}
