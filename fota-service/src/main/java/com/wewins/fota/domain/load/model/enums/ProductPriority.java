package com.wewins.fota.domain.load.model.enums;

/**
 * 产品优先级。
 *
 * <p>用于在区域负载升高时控制不同产品的周期放大幅度。</p>
 */
public enum ProductPriority {
    CRITICAL(0.75),
    HIGH(0.9),
    NORMAL(1.0),
    LOW(1.2);

    private final double intervalBias;

    ProductPriority(double intervalBias) {
        this.intervalBias = intervalBias;
    }

    public double getIntervalBias() {
        return intervalBias;
    }

    public static ProductPriority fromValue(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        for (ProductPriority priority : values()) {
            if (priority.name().equalsIgnoreCase(value)) {
                return priority;
            }
        }
        return NORMAL;
    }
}
