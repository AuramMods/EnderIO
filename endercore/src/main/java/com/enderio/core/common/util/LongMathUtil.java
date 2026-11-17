package com.enderio.core.common.util;

/** Integer-only bounds for storage values that can exceed float precision. */
public final class LongMathUtil {
    private LongMathUtil() {}

    public static long clamp(long value, long min, long max) {
        if (min > max) throw new IllegalArgumentException("Minimum exceeds maximum");
        return Math.max(min, Math.min(value, max));
    }

    public static int nonNegativeInt(long value) {
        return (int) clamp(value, 0, Integer.MAX_VALUE);
    }
}
