package com.wewins.fota.domain.load.repository;

import com.wewins.fota.domain.load.model.vo.LoadSnapshot;

import java.time.LocalDate;
import java.util.List;

/**
 * 负载历史存储接口
 */
public interface LoadHistoryRepository {

    /**
     * 保存负载快照到历史记录
     *
     * @param snapshot 负载快照
     */
    void save(LoadSnapshot snapshot);

    /**
     * 获取指定日期的负载历史
     *
     * @param date 日期
     * @return 负载快照列表
     */
    List<LoadSnapshot> getHistory(LocalDate date);

    /**
     * 获取最近 N 天的负载历史
     *
     * @param days 天数
     * @return 负载快照列表
     */
    List<LoadSnapshot> getRecentHistory(int days);
}
