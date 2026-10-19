package com.skiarkt.shooter.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NumbersTest {

    @Test
    public void parsesTrimmedAndCommaDecimals() {
        assertEquals(12.5, Numbers.parseLenient(" 12.5 "), 0);
        assertEquals(-3.25, Numbers.parseLenient("-3,25"), 0);
        assertEquals(0, Numbers.parseLenient("0"), 0);
    }

    @Test
    public void invalidInputIsNaN() {
        assertTrue(Double.isNaN(Numbers.parseLenient(null)));
        assertTrue(Double.isNaN(Numbers.parseLenient("")));
        assertTrue(Double.isNaN(Numbers.parseLenient("   ")));
        assertTrue(Double.isNaN(Numbers.parseLenient("abc")));
        assertTrue(Double.isNaN(Numbers.parseLenient("Infinity")));
        assertTrue(Double.isNaN(Numbers.parseLenient("1e400")));
    }
}
