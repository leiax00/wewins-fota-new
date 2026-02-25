package com.wewins.fota.application.policy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.policy.dto.UpgradePolicyPageReqDTO;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;

/**
 * 升级策略应用服务接口
 */
public interface UpgradePolicyAppService {

    /**
     * 分页查询升级策略列表
     *
     * @param reqDTO 分页查询参数
     * @return 分页结果
     */
    Page<UpgradePolicy> pagePolicies(UpgradePolicyPageReqDTO reqDTO);

    /**
     * 根据 ID 获取升级策略
     *
     * @param id 策略 ID
     * @return 升级策略实体
     */
    UpgradePolicy getById(Long id);

    /**
     * 创建升级策略
     *
     * @param policy 策略实体
     * @return 创建后的升级策略
     */
    UpgradePolicy createPolicy(UpgradePolicy policy);

    /**
     * 更新升级策略
     *
     * @param policy 策略实体
     * @return 更新后的升级策略
     */
    UpgradePolicy updatePolicy(UpgradePolicy policy);

    /**
     * 删除升级策略
     *
     * @param id 策略 ID
     * @return 是否成功
     */
    boolean deletePolicy(Long id);
}
