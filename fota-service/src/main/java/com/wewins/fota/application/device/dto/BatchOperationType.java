package com.wewins.fota.application.device.dto;

/**
 * 批量操作类型枚举
 * <p>
 * 定义支持的设备批量操作类型
 * </p>
 */
public enum BatchOperationType {

    /**
     * 按批次删除设备
     */
    DELETE_BY_BATCH,

    /**
     * 按批次修改设备标签
     */
    UPDATE_TAG_BY_BATCH,

    /**
     * 按IMEI列表修改设备标签
     */
    UPDATE_TAG_BY_IMEI,

    /**
     * 按IMEI列表修改设备批次
     */
    UPDATE_BATCH_BY_IMEI
}
