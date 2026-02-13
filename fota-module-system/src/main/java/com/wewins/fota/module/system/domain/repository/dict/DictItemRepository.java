package com.wewins.fota.module.system.domain.repository.dict;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.dto.DictItemPageReqDTO;

import java.util.List;

public interface DictItemRepository {

    void create(DictItem dictItem);

    boolean updateById(DictItem dictItem);

    boolean deleteById(Long dictItemId);

    DictItem findById(Long dictItemId);

    List<DictItem> findByDictTypeId(Long dictTypeId);

    Page<DictItem> page(DictItemPageReqDTO reqDTO);

    long countByDictTypeId(Long dictTypeId);

    long countByDictTypeIdAndValueExcludingId(Long dictTypeId, String value, Long excludeId);

    long countByDictTypeIdAndLabelExcludingId(Long dictTypeId, String label, Long excludeId);
}
