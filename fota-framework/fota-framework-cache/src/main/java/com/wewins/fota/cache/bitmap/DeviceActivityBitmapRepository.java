package com.wewins.fota.cache.bitmap;

import java.time.LocalDate;

/**
 * 设备活跃度 Bitmap 仓储接口
 * <p>
 * 用于跟踪和统计设备活跃度，基于 Redis Bitmap 实现。
 * 设备 ID 作为 bitmap 偏移量，每日创建一个 bitmap 键。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>标记设备活跃：markActive(day, deviceId)</li>
 *   <li>检查设备是否活跃：isActive(day, deviceId)</li>
 *   <li>统计活跃设备数：countActive(day)</li>
 *   <li>统计多日活跃设备（并集）：countActiveUnion(endDay, days)</li>
 *   <li>统计沉默设备数：countSilentDevices(endDay, silentDays)</li>
 * </ul>
 * </p>
 * <p>
 * 存储效率：
 * <ul>
 *   <li>1 亿设备 ≈ 12MB 存储空间</li>
 *   <li>使用 SETBIT/GETBIT/BITCOUNT/BITOP 命令</li>
 *   <li>临时键自动过期，避免内存泄漏</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-17
 */
public interface DeviceActivityBitmapRepository {

    /**
     * 标记设备活跃
     * <p>
     * 将指定日期的 bitmap 中对应设备 ID 的位置设为 1
     * </p>
     *
     * @param day      日期（格式：yyyyMMdd）
     * @param deviceId 设备 ID（作为 bitmap 偏移量）
     */
    void markActive(LocalDate day, long deviceId);

    /**
     * 检查设备是否活跃
     * <p>
     * 查询指定日期的 bitmap 中对应设备 ID 的位置是否为 1
     * </p>
     *
     * @param day      日期
     * @param deviceId 设备 ID
     * @return true-活跃，false-不活跃
     */
    boolean isActive(LocalDate day, long deviceId);

    /**
     * 统计活跃设备数
     * <p>
     * 统计指定日期的 bitmap 中位数为 1 的数量
     * </p>
     *
     * @param day 日期
     * @return 活跃设备数
     */
    long countActive(LocalDate day);

    /**
     * 统计多日活跃设备数（并集）
     * <p>
     * 对多日 bitmap 执行 BITOP OR 操作，统计并集的活跃设备数
     * </p>
     * <p>
     * 注意：此操作会产生临时键，需要设置短 TTL 避免内存泄漏
     * </p>
     *
     * @param endDay 结束日期（包含）
     * @param days   向前统计的天数
     * @return 多日活跃设备数（去重）
     */
    long countActiveUnion(LocalDate endDay, int days);

    /**
     * 统计沉默设备数
     * <p>
     * 统计指定天数内未活跃的设备数量
     * </p>
     * <p>
     * 计算方式：totalDevices - countActiveUnion(endDay, silentDays)
     * </p>
     * <p>
     * 注意：需要知道设备总数，如果无法获取则返回 -1
     * </p>
     *
     * @param endDay      结束日期（包含）
     * @param silentDays 沉默天数阈值
     * @return 沉默设备数，如果无法获取设备总数则返回 -1
     */
    long countSilentDevices(LocalDate endDay, int silentDays);
}
