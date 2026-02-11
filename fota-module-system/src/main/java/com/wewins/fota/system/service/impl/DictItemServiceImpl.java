package com.wewins.fota.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.database.wrapper.QueryWrapperX;
import com.wewins.fota.system.entity.DictItem;
import com.wewins.fota.system.entity.DictType;
import com.wewins.fota.system.mapper.DictItemMapper;
import com.wewins.fota.system.mapper.DictTypeMapper;
import com.wewins.fota.system.service.IDictItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 字典项服务实现
 * <p>
 * TODO: 添加缓存支持（@Cacheable/@CacheEvict）
 * - listItemsByTypeCode: 按字典类型编码缓存字典项列表
 * - createDictItem/updateDictItem/deleteDictItem: 清除相关缓存
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Slf4j
@Service
public class DictItemServiceImpl extends ServiceImpl<DictItemMapper, DictItem> implements IDictItemService {

    private final DictTypeMapper dictTypeMapper;

    public DictItemServiceImpl(DictTypeMapper dictTypeMapper) {
        this.dictTypeMapper = dictTypeMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO: 添加 @CacheEvict(cacheNames = "sys:dict:items", allEntries = true)
    public DictItem createDictItem(DictItem dictItem) {
        if (log.isDebugEnabled()) {
            log.debug("创建字典项: dictTypeId={}, label={}, value={}",
                    dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue());
        }

        validateDictTypeExists(dictItem.getDictTypeId());
        validateDictItemUnique(
                dictItem.getDictTypeId(),
                dictItem.getLabel(),
                dictItem.getValue(),
                null
        );

        save(dictItem);

        log.info("字典项创建成功: dictItemId={}, value={}", dictItem.getId(), dictItem.getValue());
        return dictItem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO: 添加 @CacheEvict(cacheNames = "sys:dict:items", allEntries = true)
    public DictItem updateDictItem(DictItem dictItem) {
        if (dictItem.getId() == null) {
            throw new IllegalArgumentException("字典项ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典项: dictItemId={}, dictTypeId={}, label={}, value={}",
                    dictItem.getId(), dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue());
        }

        validateDictTypeExists(dictItem.getDictTypeId());
        validateDictItemUnique(
                dictItem.getDictTypeId(),
                dictItem.getLabel(),
                dictItem.getValue(),
                dictItem.getId()
        );

        updateById(dictItem);

        log.info("字典项更新成功: dictItemId={}", dictItem.getId());
        return dictItem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO: 添加 @CacheEvict(cacheNames = "sys:dict:items", allEntries = true)
    public boolean deleteDictItem(Long dictItemId) {
        if (dictItemId == null) {
            throw new IllegalArgumentException("字典项ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典项: dictItemId={}", dictItemId);
        }

        boolean result = removeById(dictItemId);
        log.info("字典项删除成功: dictItemId={}, result={}", dictItemId, result);
        return result;
    }

    @Override
    // TODO: 添加 @Cacheable(cacheNames = "sys:dict:items", key = "#typeCode")
    public List<DictItem> listItemsByTypeCode(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }

        // 使用 last("LIMIT 1") 避免多行数据异常
        DictType dictType = dictTypeMapper.selectList(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getCode, typeCode)
                .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);

        if (dictType == null) {
            log.warn("字典类型不存在: code={}", typeCode);
            return Collections.emptyList();
        }

        return listItemsByTypeId(dictType.getId());
    }

    @Override
    public List<DictItem> listItemsByTypeId(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        validateDictTypeExists(dictTypeId);

        return list(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId)
                .orderByAsc(DictItem::getSortOrder)
                .orderByAsc(DictItem::getId));
    }

    @Override
    public Page<DictItem> pageItems(BaseRequestVo param) {
        if (param == null) {
            param = new BaseRequestVo();
        }
        param.validate();

        QueryWrapperX<DictItem> wrapper = new QueryWrapperX<>(DictItem.class)
                .applyFiltersIfPresent(param.getFilters())
                .applySortingIfPresent(param.getSortingFields());

        return page(new Page<>(param.getPage(), param.getSize()), wrapper);
    }

    /**
     * 验证字典类型是否存在
     *
     * @param dictTypeId 字典类型ID
     */
    private void validateDictTypeExists(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        DictType dictType = dictTypeMapper.selectById(dictTypeId);
        if (dictType == null) {
            throw new IllegalArgumentException("字典类型不存在");
        }
    }

    /**
     * 验证字典项唯一性
     *
     * @param dictTypeId 字典类型ID
     * @param label 字典项标签
     * @param value 字典项值
     * @param excludeId 排除的字典项ID
     */
    private void validateDictItemUnique(Long dictTypeId, String label, String value, Long excludeId) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("字典项标签不能为空");
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("字典项值不能为空");
        }

        // 验证值唯一性（同一字典类型下）
        LambdaQueryWrapper<DictItem> valueWrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getValue, value);

        if (excludeId != null) {
            valueWrapper.ne(DictItem::getId, excludeId);
        }

        Long valueCount = baseMapper.selectCount(valueWrapper);
        if (valueCount != null && valueCount > 0) {
            throw new IllegalArgumentException("字典项值已存在: " + value);
        }

        // 验证标签唯一性（同一字典类型下）
        LambdaQueryWrapper<DictItem> labelWrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getLabel, label);

        if (excludeId != null) {
            labelWrapper.ne(DictItem::getId, excludeId);
        }

        Long labelCount = baseMapper.selectCount(labelWrapper);
        if (labelCount != null && labelCount > 0) {
            throw new IllegalArgumentException("字典项标签已存在: " + label);
        }
    }
}
