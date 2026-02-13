package com.wewins.fota.module.system.infra.persistence.mybatis.repository.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.dict.DictType;
import com.wewins.fota.module.system.domain.repository.dict.DictTypeRepository;
import com.wewins.fota.module.system.dto.DictTypePageReqDTO;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.dict.DictTypeMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DictTypeRepositoryImpl implements DictTypeRepository {

    private final DictTypeMapper dictTypeMapper;

    public DictTypeRepositoryImpl(DictTypeMapper dictTypeMapper) {
        this.dictTypeMapper = dictTypeMapper;
    }

    @Override
    public void create(DictType dictType) {
        dictTypeMapper.insert(dictType);
    }

    @Override
    public boolean updateById(DictType dictType) {
        return dictTypeMapper.updateById(dictType) > 0;
    }

    @Override
    public boolean deleteById(Long dictTypeId) {
        return dictTypeMapper.deleteById(dictTypeId) > 0;
    }

    @Override
    public DictType findById(Long id) {
        return dictTypeMapper.selectById(id);
    }

    @Override
    public DictType findFirstByCode(String code) {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<DictType>()
                        .eq(DictType::getCode, code)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Page<DictType> page(DictTypePageReqDTO reqDTO) {
        return dictTypeMapper.selectPage(new Page<>(reqDTO.getPage(), reqDTO.getSize()), reqDTO.toWrapper());
    }

    @Override
    public long countByCodeExcludingId(String code, Long excludeId) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<DictType>()
                .eq(DictType::getCode, code);
        if (excludeId != null) {
            wrapper.ne(DictType::getId, excludeId);
        }
        Long count = dictTypeMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }
}
