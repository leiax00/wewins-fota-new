package com.wewins.fota.adapter.api.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.adapter.assembler.FirmwareVersionAssembler;
import com.wewins.fota.application.firmware.FirmwareVersionAppService;
import com.wewins.fota.application.firmware.dto.FirmwareVersionPageReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionReqDTO;
import com.wewins.fota.application.firmware.dto.FirmwareVersionRespDTO;
import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.api.PageResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 固件版本管理控制器
 * <p>
 * 提供固件版本 CRUD 接口
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/admin/firmware-versions")
@RequiredArgsConstructor
public class FirmwareVersionController {

    private final FirmwareVersionAppService firmwareVersionAppService;
    private final FirmwareVersionAssembler firmwareVersionAssembler;

    /**
     * 分页查询固件版本列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<PageResponse<FirmwareVersionRespDTO>> listFirmwareVersions(@ModelAttribute FirmwareVersionPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new FirmwareVersionPageReqDTO();
        }

        if (log.isDebugEnabled()) {
            log.debug("分页查询固件版本: productId={}, version={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), reqDTO.getPage(), reqDTO.getSize());
        }

        Page<?> pageResult = firmwareVersionAppService.pageFirmwareVersions(reqDTO);
        List<FirmwareVersionRespDTO> records = pageResult.getRecords().stream()
                .map(firmwareVersionAssembler::toFirmwareVersionResp)
                .toList();

        PageResponse<FirmwareVersionRespDTO> response = PageResponse.of(
                records,
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getTotal()
        );
        return ApiResponse.success(response);
    }

    /**
     * 根据产品 ID 查询固件版本列表
     *
     * @param productId 产品 ID
     * @return 固件版本列表
     */
    @GetMapping("/by-product/{productId}")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<List<FirmwareVersionRespDTO>> listByProductId(@PathVariable Long productId) {
        if (productId == null || productId <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("查询产品固件版本列表: productId={}", productId);
        }

        try {
            List<FirmwareVersion> firmwareVersions = firmwareVersionAppService.listByProductId(productId);
            List<FirmwareVersionRespDTO> records = firmwareVersions.stream()
                    .map(firmwareVersionAssembler::toFirmwareVersionResp)
                    .toList();
            return ApiResponse.success(records);
        } catch (BizException e) {
            log.warn("查询产品固件版本列表失败: productId={}, errorCode={}, message={}",
                    productId, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * 获取固件版本详情
     *
     * @param id 版本 ID
     * @return 版本详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:read')")
    public ApiResponse<FirmwareVersionRespDTO> getFirmwareVersion(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取固件版本详情: firmwareVersionId={}", id);
        }

        try {
            FirmwareVersion firmwareVersion = firmwareVersionAppService.getById(id);
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(firmwareVersion));
        } catch (BizException e) {
            log.warn("获取固件版本详情失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("获取固件版本详情参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 创建固件版本
     *
     * @param reqDTO 固件版本信息
     * @return 创建的固件版本
     */
    @PostMapping
    @PreAuthorize("@rbac.has('fota:firmware:create')")
    public ApiResponse<FirmwareVersionRespDTO> createFirmwareVersion(@RequestBody FirmwareVersionReqDTO reqDTO) {
        if (reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("创建固件版本: productId={}, version={}",
                    reqDTO.getProductId(), reqDTO.getVersion());
        }

        try {
            FirmwareVersion firmwareVersion = firmwareVersionAssembler.toFirmwareVersionEntity(reqDTO);
            firmwareVersion.setId(null);
            FirmwareVersion createdVersion = firmwareVersionAppService.createFirmwareVersion(firmwareVersion);
            log.info("固件版本创建成功: firmwareVersionId={}, productId={}, version={}",
                    createdVersion.getId(), createdVersion.getProductId(), createdVersion.getVersion());
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(createdVersion));
        } catch (BizException e) {
            log.warn("创建固件版本失败: productId={}, version={}, errorCode={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("创建固件版本参数错误: productId={}, version={}, message={}",
                    reqDTO.getProductId(), reqDTO.getVersion(), e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 更新固件版本
     *
     * @param id 固件版本 ID
     * @param reqDTO 固件版本信息
     * @return 更新后的固件版本
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:update')")
    public ApiResponse<FirmwareVersionRespDTO> updateFirmwareVersion(@PathVariable Long id, @RequestBody FirmwareVersionReqDTO reqDTO) {
        if (id == null || id <= 0 || reqDTO == null) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("更新固件版本: firmwareVersionId={}", id);
        }

        try {
            FirmwareVersion firmwareVersion = firmwareVersionAssembler.toFirmwareVersionEntity(reqDTO);
            firmwareVersion.setId(id);
            FirmwareVersion updatedVersion = firmwareVersionAppService.updateFirmwareVersion(firmwareVersion);
            log.info("固件版本更新成功: firmwareVersionId={}", updatedVersion.getId());
            return ApiResponse.success(firmwareVersionAssembler.toFirmwareVersionResp(updatedVersion));
        } catch (BizException e) {
            log.warn("更新固件版本失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("更新固件版本参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }

    /**
     * 删除固件版本
     *
     * @param id 版本 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:firmware:delete')")
    public ApiResponse<Void> deleteFirmwareVersion(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("删除固件版本: firmwareVersionId={}", id);
        }

        try {
            firmwareVersionAppService.deleteFirmwareVersion(id);
            log.info("固件版本删除成功: firmwareVersionId={}", id);
            return ApiResponse.success();
        } catch (BizException e) {
            log.warn("删除固件版本失败: firmwareVersionId={}, errorCode={}, message={}",
                    id, e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("删除固件版本参数错误: firmwareVersionId={}, message={}", id, e.getMessage());
            return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        }
    }
}
