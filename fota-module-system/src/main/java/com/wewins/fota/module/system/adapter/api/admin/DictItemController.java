package com.wewins.fota.module.system.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.DictItemAppService;
import com.wewins.fota.module.system.application.assembler.AdminApiAssembler;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.dto.DictItemPageReqDTO;
import com.wewins.fota.module.system.dto.DictItemReqDTO;
import com.wewins.fota.module.system.dto.DictItemRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典项 Controller
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/sys/dict-items")
public class DictItemController {

    private final DictItemAppService dictItemService;
    private final AdminApiAssembler adminApiAssembler;

    public DictItemController(DictItemAppService dictItemService, AdminApiAssembler adminApiAssembler) {
        this.dictItemService = dictItemService;
        this.adminApiAssembler = adminApiAssembler;
    }

    @GetMapping
    public ApiResponse<PageResponse<DictItemRespDTO>> listDictItems(@ModelAttribute DictItemPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DictItemPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询字典项: dictTypeId={}, status={}, page={}, size={}",
                    reqDTO.getDictTypeId(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<DictItem> pageResult = dictItemService.pageItems(reqDTO);
        List<DictItemRespDTO> records = adminApiAssembler.toDictItemRespList(pageResult.getRecords());

        PageResponse<DictItemRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<DictItemRespDTO> getDictItem(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取字典项详情: dictItemId={}", id);
        }

        try {
            var dictItem = dictItemService.getById(id);
            if (dictItem == null) {
                return ApiResponse.error(ErrorCode.DICT_ITEM_NOT_FOUND.getCode(), ErrorCode.DICT_ITEM_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(adminApiAssembler.toDictItemResp(dictItem));
        } catch (BizException e) {
            log.warn("获取字典项详情失败: dictItemId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取字典项详情参数错误: dictItemId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<DictItemRespDTO> createDictItem(@RequestBody DictItemReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建字典项: dictTypeId={}, label={}, value={}",
                    reqDTO.getDictTypeId(), reqDTO.getLabel(), reqDTO.getValue());
        }

        try {
            var dictItem = adminApiAssembler.toDictItemEntity(reqDTO);
            dictItem.setId(null);
            var createdItem = dictItemService.createDictItem(dictItem);
            log.info("字典项创建成功: dictItemId={}, value={}", createdItem.getId(), createdItem.getValue());
            return ApiResponse.success(adminApiAssembler.toDictItemResp(createdItem));
        } catch (BizException e) {
            log.warn("创建字典项失败: value={}, code={}, message={}", reqDTO.getValue(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建字典项参数错误: value={}, message={}", reqDTO.getValue(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<DictItemRespDTO> updateDictItem(@PathVariable Long id, @RequestBody DictItemReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典项: dictItemId={}", id);
        }

        try {
            var existingItem = dictItemService.getById(id);
            if (existingItem == null) {
                return ApiResponse.error(ErrorCode.DICT_ITEM_NOT_FOUND.getCode(), ErrorCode.DICT_ITEM_NOT_FOUND.getMessage());
            }

            var dictItem = adminApiAssembler.toDictItemEntity(reqDTO);
            dictItem.setId(id);
            var updatedItem = dictItemService.updateDictItem(dictItem);

            log.info("字典项更新成功: dictItemId={}", updatedItem.getId());
            return ApiResponse.success(adminApiAssembler.toDictItemResp(updatedItem));
        } catch (BizException e) {
            log.warn("更新字典项失败: dictItemId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新字典项参数错误: dictItemId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDictItem(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典项: dictItemId={}", id);
        }

        try {
            var existingItem = dictItemService.getById(id);
            if (existingItem == null) {
                return ApiResponse.error(ErrorCode.DICT_ITEM_NOT_FOUND.getCode(), ErrorCode.DICT_ITEM_NOT_FOUND.getMessage());
            }

            dictItemService.deleteDictItem(id);
            log.info("字典项删除成功: dictItemId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除字典项失败: dictItemId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除字典项参数错误: dictItemId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
