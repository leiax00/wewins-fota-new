package com.wewins.fota.database.annotation;

import java.lang.annotation.*;

/**
 * 标记 ClickHouse Mapper。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ClickHouseMapper {
}

