package com.wewins.fota.task.infra.persistence.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.task.domain.task.model.entity.AsyncTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 MyBatis Mapper 接口。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Mapper
public interface TaskMapper extends BaseMapper<AsyncTask> {
}
