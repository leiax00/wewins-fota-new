package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.DeviceTagPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface DeviceTagMapper {

    @Select({
            "<script>",
            "SELECT id, device_id, tag_key, tag_value, created_at, updated_at",
            "FROM device_tags",
            "WHERE device_id IN",
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<DeviceTagPO> selectByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    @Delete({
            "<script>",
            "DELETE FROM device_tags WHERE device_id IN",
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    @Insert({
            "<script>",
            "INSERT INTO device_tags (device_id, tag_key, tag_value) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.deviceId}, #{item.tagKey}, #{item.tagValue})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<DeviceTagPO> items);
}
