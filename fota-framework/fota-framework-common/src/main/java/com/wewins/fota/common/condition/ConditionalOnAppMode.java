package com.wewins.fota.common.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional activation based on app.mode.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(AppModeCondition.class)
public @interface ConditionalOnAppMode {

    String[] value();

    boolean matchIfMissing() default false;
}
