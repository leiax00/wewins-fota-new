package com.wewins.fota.web.jwt;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityExceptionHandlerTest {

    @Test
    void accessDeniedHandlerShouldSkipCommittedResponse() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/monitor/logs/stream");
        MockHttpServletResponse response = new CommittedMockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    @Test
    void authenticationEntryPointShouldSkipCommittedResponse() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/monitor/logs/stream");
        MockHttpServletResponse response = new CommittedMockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("unauthorized"));

        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    private static final class CommittedMockHttpServletResponse extends MockHttpServletResponse {

        @Override
        public boolean isCommitted() {
            return true;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }

                @Override
                public void write(int b) {
                }
            };
        }
    }
}
