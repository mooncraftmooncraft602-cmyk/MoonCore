package com.mooncore.modules.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonsTest {

    private static final long DAY = 86_400_000L;

    @Test
    void noEndReturnsMinusOne() {
        assertEquals(-1, Seasons.daysRemaining(1000, 0));
    }

    @Test
    void finishedReturnsZero() {
        assertEquals(0, Seasons.daysRemaining(10 * DAY, 5 * DAY));
    }

    @Test
    void roundsUpRemainingDays() {
        assertEquals(2, Seasons.daysRemaining(0, (long) (1.5 * DAY)));
        assertEquals(1, Seasons.daysRemaining(0, DAY));
    }

    @Test
    void endFromStart() {
        assertEquals(0, Seasons.endFromStart(1000, 0));
        assertEquals(1000 + 30 * DAY, Seasons.endFromStart(1000, 30));
    }
}
