package com.wewins.fota.module.system.infra.persistence.mybatis.repository.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.domain.repository.dict.DictItemRepository;
import com.wewins.fota.module.system.dto.DictItemPageReqDTO;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.dict.DictItemMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DictItemRepositoryImpl implements DictItemRepository {

    private final DictItemMapper dictItemMapper;

    public DictItemRepositoryImpl(DictItemMapper dictItemMapper) {
        this.dictItemMapper = dictItemMapper;
    }

    @Override
    public void create(DictItem dictItem) {
        dictItemMapper.insert(dictItem);
    }

    @Override
    public boolean updateById(DictItem dictItem) {
        return dictItemMapper.updateById(dictItem) > 0;
    }

    @Override
    public boolean deleteById(Long dictItemId) {
        return dictItemMapper.deleteById(dictItemId) > 0;
    }

    @Override
    public DictItem findById(Long dictItemId) {
        return dictItemMapper.selectById(dictItemId);
    }

    @Override
    public List<DictItem> findByDictTypeId(Long dictTypeId) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId)
                .orderByAsc(DictItem::getSortOrder)
                .orderByAsc(DictItem::getId));
    }

    @Override
    public Page<DictItem> page(DictItemPageReqDTO reqDTO) {
        return dictItemMapper.selectPage(new Page<>(reqDTO.getPage(), reqDTO.getSize()), reqDTO.toWrapper());
    }

    @Override
    public long countByDictTypeId(Long dictTypeId) {
        Long count = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId));
        return count == null ? 0L : count;
    }

    @Override
    public long countByDictTypeIdAndValueExcludingId(Long dictTypeId, String value, Long excludeId) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getValue, value);
        if (excludeId != null) {
            wrapper.ne(DictItem::getId, excludeId);
        }
        Long count = dictItemMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public long countByDictTypeIdAndLabelExcludingId(Long dictTypeId, String label, Long excludeId) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getLabel, label);
        if (excludeId != null) {
            wrapper.ne(DictItem::getId, excludeId);
        }
        Long count = dictItemMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }
}
