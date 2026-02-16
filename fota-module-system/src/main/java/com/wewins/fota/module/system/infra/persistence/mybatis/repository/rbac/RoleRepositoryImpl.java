package com.wewins.fota.module.system.infra.persistence.mybatis.repository.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.rbac.Role;
import com.wewins.fota.module.system.domain.repository.rbac.RoleRepository;
import com.wewins.fota.module.system.dto.RolePageReqDTO;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac.RoleMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMapper roleMapper;

    public RoleRepositoryImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public void create(Role role) {
        roleMapper.insert(role);
    }

    @Override
    public boolean updateById(Role role) {
        return roleMapper.updateById(role) > 0;
    }

    @Override
    public boolean deleteById(Long roleId) {
        return roleMapper.deleteById(roleId) > 0;
    }

    @Override
    public Role findById(Long roleId) {
        return roleMapper.selectById(roleId);
    }

    @Override
    public Page<Role> page(RolePageReqDTO reqDTO) {
        return roleMapper.selectPage(new Page<>(reqDTO.getPage(), reqDTO.getSize()), reqDTO.toWrapper());
    }

    @Override
    public long countByCodeExcludingId(String code, Long excludeId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        Long count = roleMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public long countByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0L;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .in(Role::getId, roleIds));
        return count == null ? 0L : count;
    }

    @Override
    public List<Role> findByIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectByIds(roleIds);
    }
}
