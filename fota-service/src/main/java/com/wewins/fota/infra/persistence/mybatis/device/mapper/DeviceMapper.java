package com.wewins.fota.infra.persistence.mybatis.device.mapper;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wewins.fota.domain.device.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 BaseMapper，提供 CRUD 操作
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Mapper
@PrimaryDbMapper
public interface DeviceMapper extends BaseMapper<Device> {
    // MyBatis-Plus BaseMapper 已提供常用 CRUD 方法
    // 如需自定义查询，在此添加方法并使用 @Select 注解或创建 XML 文件

    /**
     * 通过 IMEI 查询设备
     *
     * @param imei 设备 IMEI
     * @return 设备信息，如果不存在则返回 null
     */
    @Select("SELECT * FROM devices WHERE imei = #{imei} AND deleted_at IS NULL LIMIT 1")
    Device selectByImei(@Param("imei") String imei);
}
