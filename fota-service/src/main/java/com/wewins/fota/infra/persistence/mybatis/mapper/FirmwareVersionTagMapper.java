package com.wewins.fota.infra.persistence.mybatis.mapper;

import com.wewins.fota.database.annotation.PrimaryDbMapper;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionTagPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@PrimaryDbMapper
public interface FirmwareVersionTagMapper {

    @Select({
            "<script>",
            "SELECT id, firmware_version_id, tag_key, tag_value, created_at, updated_at",
            "FROM firmware_version_tags",
            "WHERE firmware_version_id IN",
            "<foreach collection='versionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<FirmwareVersionTagPO> selectByVersionIds(@Param("versionIds") List<Long> versionIds);

    @Delete({
            "<script>",
            "DELETE FROM firmware_version_tags WHERE firmware_version_id IN",
            "<foreach collection='versionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int deleteByVersionIds(@Param("versionIds") List<Long> versionIds);

    @Insert({
            "<script>",
            "INSERT INTO firmware_version_tags (firmware_version_id, tag_key, tag_value) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.firmwareVersionId}, #{item.tagKey}, #{item.tagValue})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("items") List<FirmwareVersionTagPO> items);
}
