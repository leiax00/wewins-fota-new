package com.wewins.fota.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wewins.fota.common.dto.BaseRequestVo;
import com.wewins.fota.system.entity.DictItem;

import java.util.List;

/**
 * 字典项服务接口
 * <p>
 * 提供字典项的 CRUD 操作
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
public interface IDictItemService extends IService<DictItem> {

    /**
     * 创建字典项
     *
     * @param dictItem 字典项信息
     * @return 创建的字典项
     */
    DictItem createDictItem(DictItem dictItem);

    /**
     * 更新字典项
     *
     * @param dictItem 字典项信息
     * @return 更新后的字典项
     */
    DictItem updateDictItem(DictItem dictItem);

    /**
     * 删除字典项（软删除）
     *
     * @param dictItemId 字典项ID
     * @return 是否删除成功
     */
    boolean deleteDictItem(Long dictItemId);

    /**
     * 根据字典类型编码查询字典项列表
     *
     * @param typeCode 字典类型编码
     * @return 字典项列表
     */
    List<DictItem> listItemsByTypeCode(String typeCode);

    /**
     * 根据字典类型ID查询字典项列表
     *
     * @param dictTypeId 字典类型ID
     * @return 字典项列表
     */
    List<DictItem> listItemsByTypeId(Long dictTypeId);

    /**
     * 分页查询字典项
     *
     * @param param 分页参数（含排序）
     * @return 分页结果
     */
    Page<DictItem> pageItems(BaseRequestVo param);
}
