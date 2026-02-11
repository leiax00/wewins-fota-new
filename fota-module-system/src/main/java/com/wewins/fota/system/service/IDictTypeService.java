package com.wewins.fota.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wewins.fota.system.dto.DictTypePageReqVO;
import com.wewins.fota.system.entity.DictType;

/**
 * 字典类型服务接口
 * <p>
 * 提供字典类型的 CRUD 操作
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
public interface IDictTypeService extends IService<DictType> {

    /**
     * 创建字典类型
     *
     * @param dictType 字典类型信息
     * @return 创建的字典类型
     */
    DictType createDictType(DictType dictType);

    /**
     * 更新字典类型
     *
     * @param dictType 字典类型信息
     * @return 更新后的字典类型
     */
    DictType updateDictType(DictType dictType);

    /**
     * 删除字典类型（软删除）
     *
     * @param dictTypeId 字典类型ID
     * @return 是否删除成功
     */
    boolean deleteDictType(Long dictTypeId);

    /**
     * 根据编码查询字典类型
     *
     * @param code 字典类型编码
     * @return 字典类型信息
     */
    DictType getByCode(String code);

    /**
     * 分页查询字典类型
     *
     * @param reqVO 分页查询参数
     * @return 分页结果
     */
    Page<DictType> pageTypes(DictTypePageReqVO reqVO);
}
