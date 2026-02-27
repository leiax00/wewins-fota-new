package com.wewins.fota.module.system.domain.repository.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.dto.UserPageReqDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserRepository {

    void create(User user);

    boolean updateById(User user);

    boolean deleteById(Long userId);

    User findById(Long userId);

    User findFirstByUsername(String username);

    List<User> findByKeywordAndStatus(String keyword, String status);

    Page<User> page(UserPageReqDTO reqDTO);

    long countByUsernameExcludingId(String username, Long excludeId);

    // ==================== 批量查询方法 ====================

    /**
     * 根据用户ID批量查询用户名称
     *
     * @param userIds 用户ID集合
     * @return 用户ID到用户名的映射
     */
    Map<Long, String> findNameByIds(Set<Long> userIds);

    /**
     * 根据用户ID查询用户名称
     *
     * @param userId 用户ID
     * @return 用户名，如果不存在返回 null
     */
    String findNameById(Long userId);
}
