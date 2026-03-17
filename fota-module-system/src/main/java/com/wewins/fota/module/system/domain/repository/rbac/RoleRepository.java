package com.wewins.fota.module.system.domain.repository.rbac;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.rbac.Role;
import com.wewins.fota.module.system.dto.RolePageReqDTO;

import java.util.List;

public interface RoleRepository {

    void create(Role role);

    boolean updateById(Role role);

    boolean deleteById(Long roleId);

    Role findById(Long roleId);

    Page<Role> page(RolePageReqDTO reqDTO);

    long countByCodeExcludingId(String code, Long excludeId);

    long countByIds(List<Long> roleIds);

    List<Role> findByIds(List<Long> roleIds);
}
