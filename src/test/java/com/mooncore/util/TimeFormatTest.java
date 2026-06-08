package com.mooncore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {

    @Test
    void formatsVariousDurations() {
        assertEquals("0s", TimeFormat.shortDuration(0));
        assertEquals("5m 0s", TimeFormat.shortDuration(300));
        assertEquals("1h 1m 1s", TimeFormat.shortDuration(3661));
        assertEquals("1j 0h 0m 0s", TimeFormat.shortDuration(86400));
    }

    @Test
    void negativeClampedToZero() {
        assertEquals("0s", TimeFormat.shortDuration(-50));
    }
}
