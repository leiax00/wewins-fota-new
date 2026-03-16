package com.wewins.fota.infra.logging;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseLogBroadcasterTest {

    @Test
    void shouldTreatAsyncRequestNotUsableAsClientDisconnect() throws Exception {
        SseLogBroadcaster broadcaster = new SseLogBroadcaster();

        Method method = SseLogBroadcaster.class.getDeclaredMethod("isClientDisconnect", Throwable.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(
                broadcaster,
                new AsyncRequestNotUsableException("Response not usable after response errors.")
        );

        assertTrue(result);
    }

    @Test
    void shouldNotTreatGenericRuntimeExceptionAsClientDisconnect() throws Exception {
        SseLogBroadcaster broadcaster = new SseLogBroadcaster();

        Method method = SseLogBroadcaster.class.getDeclaredMethod("isClientDisconnect", Throwable.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(broadcaster, new IllegalStateException("unexpected"));

        assertFalse(result);
    }
}
