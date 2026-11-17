package com.enderio.core.util;

import com.enderio.core.common.util.LongMathUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongMathUtilTests {
    @Test
    void keepsEnergyUnitsBeyondFloatingPointPrecision() {
        for (long energy : new long[] {16_777_217L, 9_007_199_254_740_993L, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            assertEquals(energy, LongMathUtil.clamp(energy, 0, Long.MAX_VALUE));
        }
    }

    @Test
    void boundsWithoutOverflowAtBothLongExtremes() {
        assertEquals(0, LongMathUtil.clamp(Long.MIN_VALUE, 0, Long.MAX_VALUE));
        assertEquals(123, LongMathUtil.clamp(Long.MAX_VALUE, -456, 123));
        assertEquals(Long.MIN_VALUE + 1, LongMathUtil.clamp(Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE - 1, LongMathUtil.clamp(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> LongMathUtil.clamp(0, 1, -1));
    }

    @Test
    void exposesNativeIntEnergyWithoutWrappingOrRounding() {
        assertEquals(0, LongMathUtil.nonNegativeInt(Long.MIN_VALUE));
        assertEquals(16_777_217, LongMathUtil.nonNegativeInt(16_777_217));
        assertEquals(Integer.MAX_VALUE, LongMathUtil.nonNegativeInt(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, LongMathUtil.nonNegativeInt((long) Integer.MAX_VALUE + 1));
        assertEquals(Integer.MAX_VALUE, LongMathUtil.nonNegativeInt(Long.MAX_VALUE));
    }
}
