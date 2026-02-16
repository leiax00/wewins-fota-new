package com.wewins.fota.module.system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树响应节点 DTO
 */
@Data
@Builder
public class PermissionTreeNodeRespDTO {

    private PermissionRespDTO permission;

    @Builder.Default
    private List<PermissionTreeNodeRespDTO> children = new ArrayList<>();
}
