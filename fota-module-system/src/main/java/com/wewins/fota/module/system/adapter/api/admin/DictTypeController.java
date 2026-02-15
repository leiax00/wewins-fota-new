package com.wewins.fota.module.system.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.dto.DictTypePageReqDTO;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.domain.entity.dict.DictType;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.DictItemAppService;
import com.wewins.fota.module.system.application.DictTypeAppService;
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

import java.util.List;

/**
 * 字典类型 Controller
 * <p>
 * 提供字典类型管理的 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/dict-types")
public class DictTypeController {

    private final DictTypeAppService dictTypeService;
    private final DictItemAppService dictItemService;

    public DictTypeController(DictTypeAppService dictTypeService, DictItemAppService dictItemService) {
        this.dictTypeService = dictTypeService;
        this.dictItemService = dictItemService;
    }

    /**
     * 分页查询字典类型
     *
     * @param reqDTO 分页查询参数
     * @return 分页字典类型列表
     */
    @GetMapping
    public ApiResponse<PageResponse<DictType>> listDictTypes(@ModelAttribute DictTypePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DictTypePageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询字典类型: status={}, page={}, size={}",
                    reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<DictType> pageResult = dictTypeService.pageTypes(reqDTO);

        PageResponse<DictType> response = PageResponse.of(
                pageResult.getRecords(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    /**
     * 获取字典类型详情
     *
     * @param id 字典类型ID
     * @return 字典类型信息
     */
    @GetMapping("/{id}")
    public ApiResponse<DictType> getDictType(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取字典类型详情: dictTypeId={}", id);
        }

        try {
            DictType dictType = dictTypeService.getById(id);
            if (dictType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(dictType);
        } catch (BizException e) {
            log.warn("获取字典类型详情失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取字典类型详情参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 按编码获取字典类型
     *
     * @param code 字典类型编码
     * @return 字典类型信息
     */
    @GetMapping("/code/{code}")
    public ApiResponse<DictType> getDictTypeByCode(@PathVariable String code) {
        if (code == null || code.isBlank()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("按编码获取字典类型: code={}", code);
        }

        try {
            DictType dictType = dictTypeService.getByCode(code);
            if (dictType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(dictType);
        } catch (BizException e) {
            log.warn("按编码获取字典类型失败: code={}, errorCode={}, message={}", code, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("按编码获取字典类型参数错误: code={}, message={}", code, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 创建字典类型
     *
     * @param dictType 字典类型信息
     * @return 创建后的字典类型
     */
    @PostMapping
    public ApiResponse<DictType> createDictType(@RequestBody DictType dictType) {
        if (dictType == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建字典类型: code={}", dictType.getCode());
        }

        try {
            dictType.setId(null);
            DictType createdType = dictTypeService.createDictType(dictType);
            log.info("字典类型创建成功: dictTypeId={}, code={}", createdType.getId(), createdType.getCode());
            return ApiResponse.success(createdType);
        } catch (BizException e) {
            log.warn("创建字典类型失败: code={}, errorCode={}, message={}", dictType.getCode(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建字典类型参数错误: code={}, message={}", dictType.getCode(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 更新字典类型
     *
     * @param id       字典类型ID
     * @param dictType 字典类型信息
     * @return 更新后的字典类型
     */
    @PutMapping("/{id}")
    public ApiResponse<DictType> updateDictType(@PathVariable Long id, @RequestBody DictType dictType) {
        if (id == null || id <= 0 || dictType == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典类型: dictTypeId={}", id);
        }

        try {
            DictType existingType = dictTypeService.getById(id);
            if (existingType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            dictType.setId(id);
            DictType updatedType = dictTypeService.updateDictType(dictType);

            log.info("字典类型更新成功: dictTypeId={}", updatedType.getId());
            return ApiResponse.success(updatedType);
        } catch (BizException e) {
            log.warn("更新字典类型失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新字典类型参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 删除字典类型
     *
     * @param id 字典类型ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDictType(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典类型: dictTypeId={}", id);
        }

        try {
            DictType existingType = dictTypeService.getById(id);
            if (existingType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            dictTypeService.deleteDictType(id);
            log.info("字典类型删除成功: dictTypeId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除字典类型失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除字典类型参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 按字典类型ID查询字典项
     *
     * @param id 字典类型ID
     * @return 字典项列表
     */
    @GetMapping("/{id}/items")
    public ApiResponse<List<DictItem>> listItemsByTypeId(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("按字典类型ID查询字典项: dictTypeId={}", id);
        }

        try {
            DictType existingType = dictTypeService.getById(id);
            if (existingType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            List<DictItem> items = dictItemService.listItemsByTypeId(id);
            return ApiResponse.success(items);
        } catch (BizException e) {
            log.warn("按字典类型ID查询字典项失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("按字典类型ID查询字典项参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    /**
     * 按字典类型编码查询字典项
     *
     * @param code 字典类型编码
     * @return 字典项列表
     */
    @GetMapping("/code/{code}/items")
    public ApiResponse<List<DictItem>> listItemsByTypeCode(@PathVariable String code) {
        if (code == null || code.isBlank()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("按字典类型编码查询字典项: code={}", code);
        }

        try {
            // 先验证字典类型是否存在（保持与其他接口的一致性）
            DictType dictType = dictTypeService.getByCode(code);
            if (dictType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            List<DictItem> items = dictItemService.listItemsByTypeId(dictType.getId());
            return ApiResponse.success(items);
        } catch (BizException e) {
            log.warn("按字典类型编码查询字典项失败: code={}, errorCode={}, message={}", code, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("按字典类型编码查询字典项参数错误: code={}, message={}", code, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
