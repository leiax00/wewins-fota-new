package com.wewins.fota.task.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.task.domain.task.model.entity.AsyncTask;
import com.wewins.fota.task.domain.task.repository.AsyncTaskRepository;
import com.wewins.fota.task.infra.persistence.mybatis.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 异步任务仓储实现。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Repository
@RequiredArgsConstructor
public class AsyncTaskRepositoryImpl implements AsyncTaskRepository {

    private final TaskMapper taskMapper;

    @Override
    public Optional<AsyncTask> findById(Long id) {
        AsyncTask task = taskMapper.selectById(id);
        return Optional.ofNullable(task);
    }

    @Override
    public AsyncTask save(AsyncTask task) {
        if (task.getId() == null) {
            // 新增 - 使用 MyBatis-Plus 的 insert 会自动生成 ID
            taskMapper.insert(task);
        } else {
            // 更新 - 使用 MyBatis-Plus 的 updateById
            taskMapper.updateById(task);
        }
        return task;
    }

    @Override
    public AsyncTask updateProgress(AsyncTask task) {
        taskMapper.updateById(task);
        return task;
    }
}
