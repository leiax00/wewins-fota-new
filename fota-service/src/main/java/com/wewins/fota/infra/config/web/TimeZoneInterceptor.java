package com.wewins.fota.infra.config.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * 时区拦截器
 * <p>
 * 从请求头中读取客户端时区信息，并存入 TimeZoneContext
 * </p>
 */
@Component
public class TimeZoneInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneInterceptor.class);
    private static final String TIME_ZONE_HEADER = "Time-Zone";
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("UTC");

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String timeZoneHeader = request.getHeader(TIME_ZONE_HEADER);
        ZoneId zoneId = DEFAULT_TIME_ZONE;

        if (timeZoneHeader != null && !timeZoneHeader.isBlank()) {
            try {
                zoneId = ZoneId.of(timeZoneHeader);
                if (log.isDebugEnabled()) {
                    log.debug("检测到客户端时区: {}", timeZoneHeader);
                }
            } catch (DateTimeException e) {
                log.warn("无效的时区标识: {}, 使用默认时区 UTC", timeZoneHeader);
            }
        }

        TimeZoneContext.setTimeZone(zoneId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        TimeZoneContext.clear();
    }
}
