package com.wewins.fota.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.system.dto.DictItemPageReqVO;
import com.wewins.fota.system.dto.PageResponse;
import com.wewins.fota.system.dto.Response;
import com.wewins.fota.system.entity.DictItem;
import com.wewins.fota.system.exception.BizException;
import com.wewins.fota.system.exception.ErrorCode;
import com.wewins.fota.system.service.IDictItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典项 Controller
 * <p>
 * 提供字典项管理的 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/dict-items")
public class DictItemController {

    private final IDictItemService dictItemService;

    public DictItemController(IDictItemService dictItemService) {
        this.dictItemService = dictItemService;
    }

    /**
     * 分页查询字典项
     *
     * @param reqVO 分页查询参数
     * @return 分页字典项列表
     */
    @GetMapping
    public Response<PageResponse<DictItem>> listDictItems(@ModelAttribute DictItemPageReqVO reqVO) {
        if (reqVO == null) {
            reqVO = new DictItemPageReqVO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询字典项: dictTypeId={}, status={}, page={}, size={}",
                    reqVO.getDictTypeId(), reqVO.getStatus(), reqVO.getPage(), reqVO.getSize());
        }

        Page<DictItem> pageResult = dictItemService.pageItems(reqVO);

        PageResponse<DictItem> response = PageResponse.of(
                pageResult.getRecords(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return Response.success(response);
    }

    /**
     * 获取字典项详情
     *
     * @param id 字典项ID
     * @return 字典项信息
     */
    @GetMapping("/{id}")
    public Response<DictItem> getDictItem(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取字典项详情: dictItemId={}", id);
        }

        try {
            DictItem dictItem = dictItemService.getById(id);
            if (dictItem == null) {
                return Response.error(ErrorCode.DICT_ITEM_NOT_FOUND.getCode(), ErrorCode.DICT_ITEM_NOT_FOUND.getMessage());
            }
            return Response.success(dictItem);
        } catch (BizException e) {
            log.warn("获取字典项详情失败: dictItemId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取字典项详情参数错误: dictItemId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 创建字典项
     *
     * @param dictItem 字典项信息
     * @return 创建后的字典项
     */
    @PostMapping
    public Response<DictItem> createDictItem(@RequestBody DictItem dictItem) {
        if (dictItem == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建字典项: dictTypeId={}, label={}, value={}",
                    dictItem.getDictTypeId(), dictItem.getLabel(), dictItem.getValue());
        }

        try {
            dictItem.setId(null);
            DictItem createdItem = dictItemService.createDictItem(dictItem);
            log.info("字典项创建成功: dictItemId={}, value={}", createdItem.getId(), createdItem.getValue());
            return Response.success(createdItem);
        } catch (BizException e) {
            log.warn("创建字典项失败: value={}, code={}, message={}", dictItem.getValue(), e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建字典项参数错误: value={}, message={}", dictItem.getValue(), e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 更新字典项
     *
     * @param id       字典项ID
     * @param dictItem 字典项信息
     * @return 更新后的字典项
     */
    @PutMapping("/{id}")
    public Response<DictItem> updateDictItem(@PathVariable Long id, @RequestBody DictItem dictItem) {
        if (id == null || id <= 0 || dictItem == null) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典项: dictItemId={}", id);
        }

        try {
            DictItem existingItem = dictItemService.getById(id);
            if (existingItem == null) {
                return Response.error(ErrorCode.DICT_ITEM_NOT_FOUND.getCode(), ErrorCode.DICT_ITEM_NOT_FOUND.getMessage());
            }

            dictItem.setId(id);
            DictItem updatedItem = dictItemService.updateDictItem(dictItem);

            log.info("字典项更新成功: dictItemId={}", updatedItem.getId());
            return Response.success(updatedItem);
        } catch (BizException e) {
            log.warn("更新字典项失败: dictItemId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新字典项参数错误: dictItemId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 删除字典项
     *
     * @param id 字典项ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Response<Void> deleteDictItem(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典项: dictItemId={}", id);
        }

        try {
            DictItem existingItem = dictItemService.getById(id);
            if (existingItem == null) {
                return Response.error(ErrorCode.DICT_ITEM_NOT_FOUND.getCode(), ErrorCode.DICT_ITEM_NOT_FOUND.getMessage());
            }

            dictItemService.deleteDictItem(id);
            log.info("字典项删除成功: dictItemId={}", id);
            return Response.success();
        } catch (BizException e) {
            log.warn("删除字典项失败: dictItemId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return Response.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除字典项参数错误: dictItemId={}, message={}", id, e.getMessage());
            return Response.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
