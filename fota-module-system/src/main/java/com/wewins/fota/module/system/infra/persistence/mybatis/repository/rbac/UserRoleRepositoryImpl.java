package com.wewins.fota.module.system.infra.persistence.mybatis.repository.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.module.system.domain.entity.rbac.UserRole;
import com.wewins.fota.module.system.domain.repository.rbac.UserRoleRepository;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.rbac.UserRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserRoleRepositoryImpl implements UserRoleRepository {

    private final UserRoleMapper userRoleMapper;

    public UserRoleRepositoryImpl(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public long countByRoleId(Long roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, roleId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteByUserId(Long userId) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
    }

    @Override
    public void saveBatch(List<UserRole> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return;
        }
        for (UserRole userRole : userRoles) {
            userRoleMapper.insert(userRole);
        }
    }

    @Override
    public List<UserRole> findByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
    }
}
