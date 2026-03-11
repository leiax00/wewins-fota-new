package com.wewins.fota.common.util;

/**
 * Common time constants expressed in seconds.
 */
public final class TimeConstants {

    public static final int SECONDS_PER_MINUTE = 60;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int HOURS_PER_DAY = 24;

    public static final int SECONDS_PER_HOUR = MINUTES_PER_HOUR * SECONDS_PER_MINUTE;
    public static final int SECONDS_PER_DAY = HOURS_PER_DAY * SECONDS_PER_HOUR;

    private TimeConstants() {
    }
}
