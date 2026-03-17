package com.wewins.fota.web.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JwtAuthenticationFilterTest {

    @Test
    void shouldFilterAsyncDispatch() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                null,
                username -> new User(username, "", Collections.emptyList())
        );

        Method method = JwtAuthenticationFilter.class.getDeclaredMethod("shouldNotFilterAsyncDispatch");
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(filter));
    }
}
