package com.wewins.fota.module.system.infra.persistence.mybatis.mapper.dict;
import com.wewins.fota.database.annotation.PrimaryDbMapper;

import com.wewins.fota.database.mapper.BaseMapperX;
import com.wewins.fota.module.system.domain.entity.dict.DictItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典项 Mapper
 *
 * @author FOTA Team
 * @since 2026-02-06
 */
@Mapper
@PrimaryDbMapper
public interface DictItemMapper extends BaseMapperX<DictItem> {
}
