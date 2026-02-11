package com.wewins.fota.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.database.wrapper.LambdaQueryWrapperX;
import com.wewins.fota.system.entity.DictItem;
import com.wewins.fota.system.entity.DictType;
import com.wewins.fota.system.mapper.DictItemMapper;
import com.wewins.fota.system.mapper.DictTypeMapper;
import com.wewins.fota.system.service.IDictTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典类型服务实现
 * <p>
 * TODO: 添加缓存支持（@Cacheable/@CacheEvict）
 * - getByCode: 按编码缓存字典类型
 * - createDictType/updateDictType/deleteDictType: 清除相关缓存
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Slf4j
@Service
public class DictTypeServiceImpl extends ServiceImpl<DictTypeMapper, DictType> implements IDictTypeService {

    private final DictItemMapper dictItemMapper;

    public DictTypeServiceImpl(DictItemMapper dictItemMapper) {
        this.dictItemMapper = dictItemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO: 添加 @CacheEvict(cacheNames = "sys:dict:type", allEntries = true)
    public DictType createDictType(DictType dictType) {
        if (log.isDebugEnabled()) {
            log.debug("创建字典类型: code={}", dictType.getCode());
        }

        validateTypeCodeUnique(dictType.getCode(), null);
        save(dictType);

        log.info("字典类型创建成功: dictTypeId={}, code={}", dictType.getId(), dictType.getCode());
        return dictType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO: 添加 @CacheEvict(cacheNames = "sys:dict:type", allEntries = true)
    public DictType updateDictType(DictType dictType) {
        if (dictType.getId() == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典类型: dictTypeId={}, code={}", dictType.getId(), dictType.getCode());
        }

        validateTypeCodeUnique(dictType.getCode(), dictType.getId());
        updateById(dictType);

        log.info("字典类型更新成功: dictTypeId={}", dictType.getId());
        return dictType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO: 添加 @CacheEvict(cacheNames = "sys:dict:type", allEntries = true)
    public boolean deleteDictType(Long dictTypeId) {
        if (dictTypeId == null) {
            throw new IllegalArgumentException("字典类型ID不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典类型: dictTypeId={}", dictTypeId);
        }

        // 检查是否有字典项关联该字典类型
        Long itemCount = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictTypeId, dictTypeId));

        if (itemCount != null && itemCount > 0) {
            throw new IllegalStateException("字典类型下存在字典项，无法删除");
        }

        boolean result = removeById(dictTypeId);
        log.info("字典类型删除成功: dictTypeId={}, result={}", dictTypeId, result);
        return result;
    }

    @Override
    // TODO: 添加 @Cacheable(cacheNames = "sys:dict:type", key = "#code")
    public DictType getByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }

        // 使用 last("LIMIT 1") 确保只返回一条，避免多行数据异常
        return list(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getCode, code)
                .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<DictType> listTypes(String keyword, String status) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(DictType::getCode, keyword)
                    .or()
                    .like(DictType::getName, keyword)
                    .or()
                    .like(DictType::getDescription, keyword));
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(DictType::getStatus, status);
        }

        wrapper.orderByDesc(DictType::getId);
        return list(wrapper);
    }

    @Override
    public Page<DictType> pageTypes(BaseRequestVo param) {
        if (param == null) {
            param = new BaseRequestVo();
        }
        param.validate();

        LambdaQueryWrapperX<DictType> wrapper = new LambdaQueryWrapperX<>(DictType.class)
                .likeAnyIfPresent(param.getKeyword(),
                        "code", "name", "description")
                .applyFiltersIfPresent(param.getFilters())
                .applySortingIfPresent(param.getSortingFields());

        return page(new Page<>(param.getPage(), param.getSize()), wrapper);
    }

    /**
     * 验证字典类型编码唯一性
     *
     * @param code 字典类型编码
     * @param excludeId 排除的字典类型ID
     */
    private void validateTypeCodeUnique(String code, Long excludeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }

        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<DictType>()
                .eq(DictType::getCode, code);

        if (excludeId != null) {
            wrapper.ne(DictType::getId, excludeId);
        }

        Long count = baseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("字典类型编码已存在: " + code);
        }
    }
}
