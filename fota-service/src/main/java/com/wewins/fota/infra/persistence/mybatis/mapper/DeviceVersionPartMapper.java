package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceVersionPartPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface DeviceVersionPartMapper {

    @Select({
            "<script>",
            "SELECT id, device_id, part_name, version_id, version, internal_version, is_primary, updated_at",
            "FROM device_version_parts",
            "WHERE device_id IN",
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<DeviceVersionPartPO> selectByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    @Delete({
            "<script>",
            "DELETE FROM device_version_parts WHERE device_id IN",
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    @Insert({
            "<script>",
            "INSERT INTO device_version_parts",
            "(device_id, part_name, version_id, version, internal_version, is_primary, updated_at) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.deviceId}, #{item.partName}, #{item.versionId}, #{item.version}, #{item.internalVersion}, #{item.isPrimary}, #{item.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<DeviceVersionPartPO> items);
}
