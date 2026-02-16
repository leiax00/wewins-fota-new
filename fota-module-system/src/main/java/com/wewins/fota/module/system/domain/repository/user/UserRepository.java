package com.wewins.fota.module.system.domain.repository.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.dto.UserPageReqDTO;

import java.util.List;

public interface UserRepository {

    void create(User user);

    boolean updateById(User user);

    boolean deleteById(Long userId);

    User findById(Long userId);

    User findFirstByUsername(String username);

    List<User> findByKeywordAndStatus(String keyword, String status);

    Page<User> page(UserPageReqDTO reqDTO);

    long countByUsernameExcludingId(String username, Long excludeId);
}
