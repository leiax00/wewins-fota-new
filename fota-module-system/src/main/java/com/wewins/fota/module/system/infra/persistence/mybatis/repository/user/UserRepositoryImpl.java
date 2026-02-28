package com.wewins.fota.module.system.infra.persistence.mybatis.repository.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.domain.repository.user.UserRepository;
import com.wewins.fota.module.system.dto.UserPageReqDTO;
import com.wewins.fota.module.system.infra.persistence.mybatis.mapper.user.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void create(User user) {
        userMapper.insert(user);
    }

    @Override
    public boolean updateById(User user) {
        return userMapper.updateById(user) > 0;
    }

    @Override
    public boolean deleteById(Long userId) {
        return userMapper.deleteById(userId) > 0;
    }

    @Override
    public User findById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User findFirstByUsername(String username) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<User> findByKeywordAndStatus(String keyword, String status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getDisplayName, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getId);
        return userMapper.selectList(wrapper);
    }

    @Override
    public Page<User> page(UserPageReqDTO reqDTO) {
        return userMapper.selectPage(new Page<>(reqDTO.getPage(), reqDTO.getSize()), reqDTO.toWrapper());
    }

    @Override
    public long countByUsernameExcludingId(String username, Long excludeId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    // ==================== 批量查询方法实现 ====================

    @Override
    public Map<Long, String> findNameByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds)
                        .select(User::getId, User::getDisplayName, User::getUsername)
        );

        return users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
                ));
    }

    @Override
    public String findNameById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
    }
}
