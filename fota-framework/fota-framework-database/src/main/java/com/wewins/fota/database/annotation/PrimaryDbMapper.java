package com.wewins.fota.database.annotation;

import java.lang.annotation.*;

/**
 * 标记主库（PostgreSQL）Mapper。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PrimaryDbMapper {
}

