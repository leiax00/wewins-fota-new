package com.wewins.fota.module.system.application;

import com.wewins.fota.module.system.dto.menu.UserMenuNodeDTO;

import java.util.List;

/**
 * 菜单应用服务
 */
public interface MenuAppService {

    /**
     * 获取用户菜单树
     *
     * @param userId 用户ID
     * @return 菜单树列表
     */
    List<UserMenuNodeDTO> getUserMenuTree(Long userId);

    /**
     * 清除用户菜单缓存
     *
     * @param userId 用户ID
     */
    void evictUserMenuCache(Long userId);

    /**
     * 清除所有用户菜单缓存
     * <p>
     * 用于权限配置变更时，清除所有用户的菜单缓存
     * </p>
     */
    void evictAllUserMenusCache();
}
