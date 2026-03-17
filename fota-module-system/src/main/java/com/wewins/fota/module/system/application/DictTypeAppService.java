package com.wewins.fota.module.system.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.dict.DictType;
import com.wewins.fota.module.system.domain.repository.dict.DictItemRepository;
import com.wewins.fota.module.system.domain.repository.dict.DictTypeRepository;
import com.wewins.fota.module.system.dto.DictTypePageReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DictTypeAppService  {

    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;

    public DictTypeAppService(DictTypeRepository dictTypeRepository,
                               DictItemRepository dictItemRepository) {
        this.dictTypeRepository = dictTypeRepository;
        this.dictItemRepository = dictItemRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public DictType createDictType(DictType dictType) {
        if (log.isDebugEnabled()) {
            log.debug("创建字典类型: code={}", dictType.getCode());
        }

        validateTypeCodeUnique(dictType.getCode(), null);
        dictTypeRepository.create(dictType);

        log.info("字典类型创建成功: dictTypeId={}, code={}", dictType.getId(), dictType.getCode());
        return dictType;
    }

    @Transactional(rollbackFor = Exception.class)
    public DictType updateDictType(DictType dictType) {
        if (dictType.getId() == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典类型: dictTypeId={}, code={}", dictType.getId(), dictType.getCode());
        }

        validateTypeCodeUnique(dictType.getCode(), dictType.getId());
        dictTypeRepository.updateById(dictType);

        log.info("字典类型更新成功: dictTypeId={}", dictType.getId());
        return dictType;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDictType(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典类型: dictTypeId={}", dictTypeId);
        }

        if (dictItemRepository.countByDictTypeId(dictTypeId) > 0) {
            throw new IllegalStateException("字典类型下存在字典项，无法删除");
        }

        boolean result = dictTypeRepository.deleteById(dictTypeId);
        log.info("字典类型删除成功: dictTypeId={}, result={}", dictTypeId, result);
        return result;
    }

    public DictType getById(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }
        return dictTypeRepository.findById(dictTypeId);
    }

    public DictType getByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }
        return dictTypeRepository.findFirstByCode(code);
    }

    public Page<DictType> pageTypes(DictTypePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DictTypePageReqDTO();
        }
        reqDTO.validate();
        return dictTypeRepository.page(reqDTO);
    }

    private void validateTypeCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }

        if (dictTypeRepository.countByCodeExcludingId(code, excludeId) > 0) {
            throw new IllegalArgumentException("字典类型编码已存在: " + code);
        }
    }
}
