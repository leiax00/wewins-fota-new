package com.wewins.fota.module.system.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.DictItemAppService;
import com.wewins.fota.module.system.application.DictTypeAppService;
import com.wewins.fota.module.system.application.assembler.AdminApiAssembler;
import com.wewins.fota.module.system.dto.DictItemRespDTO;
import com.wewins.fota.module.system.dto.DictTypePageReqDTO;
import com.wewins.fota.module.system.dto.DictTypeReqDTO;
import com.wewins.fota.module.system.dto.DictTypeRespDTO;
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
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/dict-types")
public class DictTypeController {

    private final DictTypeAppService dictTypeService;
    private final DictItemAppService dictItemService;
    private final AdminApiAssembler adminApiAssembler;

    public DictTypeController(DictTypeAppService dictTypeService,
                              DictItemAppService dictItemService,
                              AdminApiAssembler adminApiAssembler) {
        this.dictTypeService = dictTypeService;
        this.dictItemService = dictItemService;
        this.adminApiAssembler = adminApiAssembler;
    }

    @GetMapping
    public ApiResponse<PageResponse<DictTypeRespDTO>> listDictTypes(@ModelAttribute DictTypePageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new DictTypePageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询字典类型: status={}, page={}, size={}",
                    reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<?> pageResult = dictTypeService.pageTypes(reqDTO);
        List<DictTypeRespDTO> records = adminApiAssembler.toDictTypeRespListFromUnknown(pageResult.getRecords());

        PageResponse<DictTypeRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<DictTypeRespDTO> getDictType(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取字典类型详情: dictTypeId={}", id);
        }

        try {
            var dictType = dictTypeService.getById(id);
            if (dictType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(adminApiAssembler.toDictTypeResp(dictType));
        } catch (BizException e) {
            log.warn("获取字典类型详情失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取字典类型详情参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @GetMapping("/code/{code}")
    public ApiResponse<DictTypeRespDTO> getDictTypeByCode(@PathVariable String code) {
        if (code == null || code.isBlank()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("按编码获取字典类型: code={}", code);
        }

        try {
            var dictType = dictTypeService.getByCode(code);
            if (dictType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }
            return ApiResponse.success(adminApiAssembler.toDictTypeResp(dictType));
        } catch (BizException e) {
            log.warn("按编码获取字典类型失败: code={}, errorCode={}, message={}", code, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("按编码获取字典类型参数错误: code={}, message={}", code, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<DictTypeRespDTO> createDictType(@RequestBody DictTypeReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建字典类型: code={}", reqDTO.getCode());
        }

        try {
            var dictType = adminApiAssembler.toDictTypeEntity(reqDTO);
            dictType.setId(null);
            var createdType = dictTypeService.createDictType(dictType);
            log.info("字典类型创建成功: dictTypeId={}, code={}", createdType.getId(), createdType.getCode());
            return ApiResponse.success(adminApiAssembler.toDictTypeResp(createdType));
        } catch (BizException e) {
            log.warn("创建字典类型失败: code={}, errorCode={}, message={}", reqDTO.getCode(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建字典类型参数错误: code={}, message={}", reqDTO.getCode(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<DictTypeRespDTO> updateDictType(@PathVariable Long id, @RequestBody DictTypeReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新字典类型: dictTypeId={}", id);
        }

        try {
            var existingType = dictTypeService.getById(id);
            if (existingType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            var dictType = adminApiAssembler.toDictTypeEntity(reqDTO);
            dictType.setId(id);
            var updatedType = dictTypeService.updateDictType(dictType);

            log.info("字典类型更新成功: dictTypeId={}", updatedType.getId());
            return ApiResponse.success(adminApiAssembler.toDictTypeResp(updatedType));
        } catch (BizException e) {
            log.warn("更新字典类型失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新字典类型参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDictType(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除字典类型: dictTypeId={}", id);
        }

        try {
            var existingType = dictTypeService.getById(id);
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

    @GetMapping("/{id}/items")
    public ApiResponse<List<DictItemRespDTO>> listItemsByTypeId(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("按字典类型ID查询字典项: dictTypeId={}", id);
        }

        try {
            var existingType = dictTypeService.getById(id);
            if (existingType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            var items = dictItemService.listItemsByTypeId(id);
            return ApiResponse.success(adminApiAssembler.toDictItemRespList(items));
        } catch (BizException e) {
            log.warn("按字典类型ID查询字典项失败: dictTypeId={}, code={}, message={}", id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("按字典类型ID查询字典项参数错误: dictTypeId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }

    @GetMapping("/code/{code}/items")
    public ApiResponse<List<DictItemRespDTO>> listItemsByTypeCode(@PathVariable String code) {
        if (code == null || code.isBlank()) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("按字典类型编码查询字典项: code={}", code);
        }

        try {
            var dictType = dictTypeService.getByCode(code);
            if (dictType == null) {
                return ApiResponse.error(ErrorCode.DICT_TYPE_NOT_FOUND.getCode(), ErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
            }

            var items = dictItemService.listItemsByTypeId(dictType.getId());
            return ApiResponse.success(adminApiAssembler.toDictItemRespList(items));
        } catch (BizException e) {
            log.warn("按字典类型编码查询字典项失败: code={}, errorCode={}, message={}", code, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("按字典类型编码查询字典项参数错误: code={}, message={}", code, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }
    }
}
