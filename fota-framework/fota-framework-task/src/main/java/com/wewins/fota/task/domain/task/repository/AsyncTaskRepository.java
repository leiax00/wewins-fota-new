package com.wewins.fota.task.domain.task.repository;

import com.wewins.fota.task.domain.task.model.entity.AsyncTask;

import java.util.Optional;

/**
 * 异步任务仓储接口。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
public interface AsyncTaskRepository {

    /**
     * 根据 ID 查询任务
     *
     * @param id 任务 ID（Long 类型）
     * @return 任务实体
     */
    Optional<AsyncTask> findById(Long id);

    /**
     * 保存任务
     *
     * @param task 任务实体
     * @return 保存后的任务实体
     */
    AsyncTask save(AsyncTask task);

    /**
     * 更新任务进度
     *
     * @param task 任务实体
     * @return 更新后的任务实体
     */
    AsyncTask updateProgress(AsyncTask task);
}
