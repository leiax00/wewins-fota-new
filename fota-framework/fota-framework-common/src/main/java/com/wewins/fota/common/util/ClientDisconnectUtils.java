package com.wewins.fota.common.util;

/**
 * 客户端断开连接判断工具。
 */
public final class ClientDisconnectUtils {

    private ClientDisconnectUtils() {
    }

    public static boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset by peer")
                        || message.contains("断开的管道")
                        || message.contains("你的主机中的软件中止了一个已建立的连接")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
