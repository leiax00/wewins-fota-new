package com.wewins.fota.common.condition;

import lombok.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * Runtime condition for app.mode.
 */
public class AppModeCondition implements Condition {

    @Override
    public boolean matches(@NonNull ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnAppMode.class.getName());
        if (attrs == null) {
            return true;
        }

        String[] expectedModes = (String[]) attrs.get("value");
        boolean matchIfMissing = Boolean.TRUE.equals(attrs.get("matchIfMissing"));

        String actualMode = context.getEnvironment().getProperty("app.mode");
        if (actualMode == null || actualMode.isBlank()) {
            return matchIfMissing;
        }

        if (expectedModes == null || expectedModes.length == 0) {
            return true;
        }

        for (String expectedMode : expectedModes) {
            if (actualMode.equalsIgnoreCase(expectedMode)) {
                return true;
            }
        }
        return false;
    }
}
