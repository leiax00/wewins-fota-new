package com.wewins.fota.module.system.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.module.system.application.event.DictItemChangedEvent;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.domain.entity.dict.DictType;
import com.wewins.fota.module.system.domain.repository.dict.DictItemRepository;
import com.wewins.fota.module.system.domain.repository.dict.DictTypeRepository;
import com.wewins.fota.module.system.dto.DictItemPageReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DictItemAppService  {

    private final DictItemRepository dictItemRepository;
    private final DictTypeRepository dictTypeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DictItemAppService(DictItemRepository dictItemRepository,
                               DictTypeRepository dictTypeRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.dictItemRepository = dictItemRepository;
        this.dictTypeRepository = dictTypeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public DictItem createDictItem(DictItem dictItem) {
        if (log.isDebugEnabled()) {
            log.debug("创建字典项: dictTypeId={}, label={}, value={}", dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue());
        }

        DictType dictType = validateDictTypeExists(dictItem.getDictTypeId());
        validateDictItemUnique(dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue(), null);

        dictItemRepository.create(dictItem);
        publishChangedEvent(DictItemChangedEvent.Action.CREATED, dictType.getCode(), null, dictItem);
        log.info("字典项创建成功: dictItemId={}, value={}", dictItem.getId(), dictItem.getValue());
        return dictItem;
    }

    @Transactional(rollbackFor = Exception.class)
    public DictItem updateDictItem(DictItem dictItem) {
        if (dictItem.getId() == null) {
            throw new IllegalArgumentException("字典项ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典项: dictItemId={}, dictTypeId={}, label={}, value={}",
                    dictItem.getId(), dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue());
        }

        DictItem existingItem = dictItemRepository.findById(dictItem.getId());
        if (existingItem == null) {
            throw new IllegalArgumentException("字典项不存在");
        }

        DictType dictType = validateDictTypeExists(dictItem.getDictTypeId());
        validateDictItemUnique(dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue(), dictItem.getId());

        dictItemRepository.updateById(dictItem);
        publishChangedEvent(DictItemChangedEvent.Action.UPDATED, dictType.getCode(), existingItem, dictItem);
        log.info("字典项更新成功: dictItemId={}", dictItem.getId());
        return dictItem;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDictItem(Long dictItemId) {
        if (dictItemId == null) {
            throw new IllegalArgumentException("字典项ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典项: dictItemId={}", dictItemId);
        }

        DictItem existingItem = dictItemRepository.findById(dictItemId);
        if (existingItem == null) {
            throw new IllegalArgumentException("字典项不存在");
        }
        DictType dictType = validateDictTypeExists(existingItem.getDictTypeId());

        boolean result = dictItemRepository.deleteById(dictItemId);
        if (result) {
            publishChangedEvent(DictItemChangedEvent.Action.DELETED, dictType.getCode(), existingItem, null);
        }
        log.info("字典项删除成功: dictItemId={}, result={}", dictItemId, result);
        return result;
    }

    public DictItem getById(Long dictItemId) {
        if (dictItemId == null) {
            throw new IllegalArgumentException("字典项ID不能为空");
        }
        return dictItemRepository.findById(dictItemId);
    }

    public List<DictItem> listItemsByTypeCode(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }

        DictType dictType = dictTypeRepository.findFirstByCode(typeCode);
        if (dictType == null) {
            log.warn("字典类型不存在: code={}", typeCode);
            return Collections.emptyList();
        }

        return listItemsByTypeId(dictType.getId());
    }

    public List<DictItem> listItemsByTypeId(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        validateDictTypeExists(dictTypeId);
        return dictItemRepository.findByDictTypeId(dictTypeId);
    }

    public Page<DictItem> pageItems(DictItemPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DictItemPageReqDTO();
        }
        reqDTO.validate();
        return dictItemRepository.page(reqDTO);
    }

    private DictType validateDictTypeExists(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        DictType dictType = dictTypeRepository.findById(dictTypeId);
        if (dictType == null) {
            throw new IllegalArgumentException("字典类型不存在");
        }
        return dictType;
    }

    private void validateDictItemUnique(Long dictTypeId, String label, String value, Long excludeId) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("字典项标签不能为空");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("字典项值不能为空");
        }

        if (dictItemRepository.countByDictTypeIdAndValueExcludingId(dictTypeId, value, excludeId) > 0) {
            throw new IllegalArgumentException("字典项值已存在: " + value);
        }

        if (dictItemRepository.countByDictTypeIdAndLabelExcludingId(dictTypeId, label, excludeId) > 0) {
            throw new IllegalArgumentException("字典项标签已存在: " + label);
        }
    }

    private void publishChangedEvent(DictItemChangedEvent.Action action, String dictTypeCode, DictItem before, DictItem after) {
        eventPublisher.publishEvent(DictItemChangedEvent.builder()
                .action(action)
                .itemId(after != null ? after.getId() : before != null ? before.getId() : null)
                .dictTypeCode(dictTypeCode)
                .beforeValue(before != null ? before.getValue() : null)
                .beforeExtra(copyJson(before != null ? before.getExtra() : null))
                .beforeUpdatedBy(before != null ? before.getUpdatedBy() : null)
                .afterValue(after != null ? after.getValue() : null)
                .afterExtra(copyJson(after != null ? after.getExtra() : null))
                .afterUpdatedBy(after != null ? after.getUpdatedBy() : null)
                .build());
    }

    private JsonNode copyJson(JsonNode source) {
        return source == null ? null : source.deepCopy();
    }
}
