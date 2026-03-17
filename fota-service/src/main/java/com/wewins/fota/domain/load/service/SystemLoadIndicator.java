package com.wewins.fota.domain.load.service;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;

/**
 * 系统负载指标服务接口
 * <p>
 * 提供系统负载评估能力，用于动态周期调整和智能退避算法
 * </p>
 */
public interface SystemLoadIndicator {

    /**
     * 获取当前负载快照
     * <p>
     * 带 1 秒缓存，避免频繁采集指标
     * </p>
     *
     * @return 负载快照
     */
    LoadSnapshot getSnapshot();

    /**
     * 获取当前负载级别
     *
     * @return 负载级别
     */
    LoadLevel getLoadLevel();

    /**
     * 判断系统是否过载
     * <p>
     * 过载定义：HIGH 或 CRITICAL 级别
     * </p>
     *
     * @return 是否过载
     */
    boolean isOverloaded();
}
