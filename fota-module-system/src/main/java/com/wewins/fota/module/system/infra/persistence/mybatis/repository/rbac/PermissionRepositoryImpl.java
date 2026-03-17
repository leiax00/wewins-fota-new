package com.wewins.fota.module.system.infra.persistence.mybatis.repository.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.rbac.Permission;
import com.wewins.fota.module.system.domain.repository.rbac.PermissionRepository;
import com.wewins.fota.module.system.dto.PermissionPageReqDTO;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac.PermissionMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class PermissionRepositoryImpl implements PermissionRepository {

    private final PermissionMapper permissionMapper;

    public PermissionRepositoryImpl(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    public void create(Permission permission) {
        permissionMapper.insert(permission);
    }

    @Override
    public boolean updateById(Permission permission) {
        return permissionMapper.updateById(permission) > 0;
    }

    @Override
    public boolean deleteById(Long permissionId) {
        return permissionMapper.deleteById(permissionId) > 0;
    }

    @Override
    public Permission findById(Long permissionId) {
        return permissionMapper.selectById(permissionId);
    }

    @Override
    public Page<Permission> page(PermissionPageReqDTO reqDTO) {
        return permissionMapper.selectPage(new Page<>(reqDTO.getPage(), reqDTO.getSize()), reqDTO.toWrapper());
    }

    @Override
    public List<Permission> findAllOrderByParentIdAndId() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getParentId)
                .orderByAsc(Permission::getId));
    }

    @Override
    public long countByIds(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return 0L;
        }
        Long count = permissionMapper.selectCount(new LambdaQueryWrapper<Permission>()
                .in(Permission::getId, permissionIds));
        return count == null ? 0L : count;
    }

    @Override
    public List<Permission> findByIds(List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionMapper.selectByIds(permissionIds);
    }

    @Override
    public long countByCodeExcludingId(String code, Long excludeId) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<Permission>()
                .eq(Permission::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Permission::getId, excludeId);
        }
        Long count = permissionMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public long countByParentId(Long parentId) {
        Long count = permissionMapper.selectCount(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getParentId, parentId));
        return count == null ? 0L : count;
    }

    @Override
    public List<Permission> findMenuPermissionsByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return permissionMapper.findMenuPermissionsByUserId(userId);
    }
}
