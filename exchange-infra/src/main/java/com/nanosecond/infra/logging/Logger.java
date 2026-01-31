package com.nanosecond.infra.logging;

/**
 * <b>Binary Low-Latency Logger Interface.</b>
 * <p>
 * In HFT, we generally do not log strings like "Order Accepted" because:
 * 1. Strings create garbage.
 * 2. Formatting strings takes CPU time.
 * </p>
 * <p>
 * Instead, we treat logs as "Events". This interface allows components to write
 * binary
 * data directly to the log.
 * </p>
 */
public interface Logger {
    void log(CharSequence message);

    void log(long value);

    void log(CharSequence message, long value);
}
