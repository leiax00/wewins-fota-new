package com.wewins.fota.module.system.application.assembler;

import com.wewins.fota.module.system.application.dto.PermissionTreeNode;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import com.wewins.fota.module.system.domain.entity.dict.DictType;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.entity.rbac.Role;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.dto.DictItemReqDTO;
import com.wewins.fota.module.system.dto.DictItemRespDTO;
import com.wewins.fota.module.system.dto.DictTypeReqDTO;
import com.wewins.fota.module.system.dto.DictTypeRespDTO;
import com.wewins.fota.module.system.dto.PermissionReqDTO;
import com.wewins.fota.module.system.dto.PermissionRespDTO;
import com.wewins.fota.module.system.dto.PermissionTreeNodeRespDTO;
import com.wewins.fota.module.system.dto.RoleReqDTO;
import com.wewins.fota.module.system.dto.RoleRespDTO;
import com.wewins.fota.module.system.dto.UserReqDTO;
import com.wewins.fota.module.system.dto.UserRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Admin API DTO assembler.
 */
@Component
@RequiredArgsConstructor
public class AdminApiAssembler {

    private final PasswordEncoder passwordEncoder;

    /**
     * 将 DTO 转换为 User 实体（用于创建用户）
     * <p>
     * 注意：此方法会对明文密码进行加密处理
     * </p>
     */
    public User toUserEntity(UserReqDTO req) {
        if (req == null) {
            return null;
        }
        return User.builder()
                .username(req.getUsername())
                .displayName(req.getDisplayName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .status(req.getStatus())
                .tenantId(req.getTenantId())
                .build();
    }

    /**
     * 将 DTO 转换为 User 实体，并加密密码
     * <p>
     * 用于创建用户时，将明文密码加密后存储
     * </p>
     */
    public User toUserEntityWithPassword(UserReqDTO req) {
        User user = toUserEntity(req);
        if (user != null && req.getPassword() != null && !req.getPassword().isBlank()) {
            String encodedPassword = passwordEncoder.encode(req.getPassword());
            user.setPasswordHash(encodedPassword);
        }
        return user;
    }

    /**
     * 加密明文密码
     *
     * @param rawPassword 明文密码
     * @return 加密后的密码哈希
     */
    public String encodePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    public UserRespDTO toUserResp(User user) {
        if (user == null) {
            return null;
        }
        return UserRespDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .tenantId(user.getTenantId())
                .createdAt(user.getCreatedAt())
                .createdBy(user.getCreatedBy())
                .updatedAt(user.getUpdatedAt())
                .updatedBy(user.getUpdatedBy())
                .build();
    }

    public List<UserRespDTO> toUserRespList(List<User> users) {
        return users == null ? List.of() : users.stream().map(this::toUserResp).toList();
    }

    public List<UserRespDTO> toUserRespListFromUnknown(List<?> users) {
        return mapListOrThrow(users, User.class, this::toUserResp);
    }

    public Role toRoleEntity(RoleReqDTO req) {
        if (req == null) {
            return null;
        }
        return Role.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .status(req.getStatus())
                .build();
    }

    public RoleRespDTO toRoleResp(Role role) {
        if (role == null) {
            return null;
        }
        return RoleRespDTO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .createdAt(role.getCreatedAt())
                .createdBy(role.getCreatedBy())
                .updatedAt(role.getUpdatedAt())
                .updatedBy(role.getUpdatedBy())
                .build();
    }

    public List<RoleRespDTO> toRoleRespList(List<Role> roles) {
        return roles == null ? List.of() : roles.stream().map(this::toRoleResp).toList();
    }

    public List<RoleRespDTO> toRoleRespListFromUnknown(List<?> roles) {
        return mapListOrThrow(roles, Role.class, this::toRoleResp);
    }

    public Permission toPermissionEntity(PermissionReqDTO req) {
        if (req == null) {
            return null;
        }
        return Permission.builder()
                .code(req.getCode())
                .name(req.getName())
                .type(req.getType())
                .path(req.getPath())
                .method(req.getMethod())
                .parentId(req.getParentId())
                .status(req.getStatus())
                // 菜单路由字段
                .routePath(req.getRoutePath())
                .routeName(req.getRouteName())
                .componentKey(req.getComponentKey())
                .redirectPath(req.getRedirectPath())
                // 菜单显示字段
                .icon(req.getIcon())
                .menuSort(req.getMenuSort())
                .externalLink(req.getExternalLink())
                .build();
    }

    public PermissionRespDTO toPermissionResp(Permission permission) {
        if (permission == null) {
            return null;
        }
        return PermissionRespDTO.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .type(permission.getType())
                .path(permission.getPath())
                .method(permission.getMethod())
                .parentId(permission.getParentId())
                .status(permission.getStatus())
                .createdAt(permission.getCreatedAt())
                .createdBy(permission.getCreatedBy())
                .updatedAt(permission.getUpdatedAt())
                .updatedBy(permission.getUpdatedBy())
                // 菜单路由字段
                .routePath(permission.getRoutePath())
                .routeName(permission.getRouteName())
                .componentKey(permission.getComponentKey())
                .redirectPath(permission.getRedirectPath())
                // 菜单显示字段
                .icon(permission.getIcon())
                .menuSort(permission.getMenuSort())
                .externalLink(permission.getExternalLink())
                .build();
    }

    public List<PermissionRespDTO> toPermissionRespList(List<Permission> permissions) {
        return permissions == null ? List.of() : permissions.stream().map(this::toPermissionResp).toList();
    }

    public List<PermissionRespDTO> toPermissionRespListFromUnknown(List<?> permissions) {
        return mapListOrThrow(permissions, Permission.class, this::toPermissionResp);
    }

    public PermissionTreeNodeRespDTO toPermissionTreeNodeResp(PermissionTreeNode node) {
        if (node == null) {
            return null;
        }
        List<PermissionTreeNodeRespDTO> children = node.getChildren() == null
                ? List.of()
                : node.getChildren().stream().map(this::toPermissionTreeNodeResp).toList();

        return PermissionTreeNodeRespDTO.builder()
                .permission(toPermissionResp(node.getPermission()))
                .children(children)
                .build();
    }

    public List<PermissionTreeNodeRespDTO> toPermissionTreeNodeRespList(List<PermissionTreeNode> tree) {
        return tree == null ? List.of() : tree.stream().map(this::toPermissionTreeNodeResp).toList();
    }

    public DictType toDictTypeEntity(DictTypeReqDTO req) {
        if (req == null) {
            return null;
        }
        return DictType.builder()
                .code(req.getCode())
                .name(req.getName())
                .i18nKey(req.getI18nKey())
                .status(req.getStatus())
                .description(req.getDescription())
                .build();
    }

    public DictTypeRespDTO toDictTypeResp(DictType dictType) {
        if (dictType == null) {
            return null;
        }
        return DictTypeRespDTO.builder()
                .id(dictType.getId())
                .code(dictType.getCode())
                .name(dictType.getName())
                .i18nKey(dictType.getI18nKey())
                .status(dictType.getStatus())
                .description(dictType.getDescription())
                .createdAt(dictType.getCreatedAt())
                .createdBy(dictType.getCreatedBy())
                .updatedAt(dictType.getUpdatedAt())
                .updatedBy(dictType.getUpdatedBy())
                .build();
    }

    public List<DictTypeRespDTO> toDictTypeRespList(List<DictType> dictTypes) {
        return dictTypes == null ? List.of() : dictTypes.stream().map(this::toDictTypeResp).toList();
    }

    public List<DictTypeRespDTO> toDictTypeRespListFromUnknown(List<?> dictTypes) {
        return mapListOrThrow(dictTypes, DictType.class, this::toDictTypeResp);
    }

    public DictItem toDictItemEntity(DictItemReqDTO req) {
        if (req == null) {
            return null;
        }
        return DictItem.builder()
                .dictTypeId(req.getDictTypeId())
                .label(req.getLabel())
                .value(req.getValue())
                .i18nKey(req.getI18nKey())
                .sortOrder(req.getSortOrder())
                .status(req.getStatus())
                .extra(req.getExtra())
                .build();
    }

    public DictItemRespDTO toDictItemResp(DictItem dictItem) {
        if (dictItem == null) {
            return null;
        }
        return DictItemRespDTO.builder()
                .id(dictItem.getId())
                .dictTypeId(dictItem.getDictTypeId())
                .label(dictItem.getLabel())
                .value(dictItem.getValue())
                .i18nKey(dictItem.getI18nKey())
                .sortOrder(dictItem.getSortOrder())
                .status(dictItem.getStatus())
                .extra(dictItem.getExtra())
                .createdAt(dictItem.getCreatedAt())
                .createdBy(dictItem.getCreatedBy())
                .updatedAt(dictItem.getUpdatedAt())
                .updatedBy(dictItem.getUpdatedBy())
                .build();
    }

    public List<DictItemRespDTO> toDictItemRespList(List<DictItem> dictItems) {
        return dictItems == null ? List.of() : dictItems.stream().map(this::toDictItemResp).toList();
    }

    public List<DictItemRespDTO> toDictItemRespListFromUnknown(List<?> dictItems) {
        return mapListOrThrow(dictItems, DictItem.class, this::toDictItemResp);
    }

    private <S, T> List<T> mapListOrThrow(List<?> source, Class<S> expectedType, Function<S, T> mapper) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        for (Object item : source) {
            if (!expectedType.isInstance(item)) {
                throw new IllegalStateException("Unexpected list item type: " + item.getClass().getName());
            }
        }
        return source.stream().map(expectedType::cast).map(mapper).toList();
    }
}
