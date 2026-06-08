package com.mooncore.modules.missions;

import com.mooncore.api.mission.MissionScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MissionPeriodTest {

    private static final long DAY = 86_400_000L;

    @Test
    void dailyKeyChangesEachDay() {
        String d0 = MissionPeriod.key(MissionScope.DAILY, 0, "s1");
        String sameDay = MissionPeriod.key(MissionScope.DAILY, DAY - 1, "s1");
        String nextDay = MissionPeriod.key(MissionScope.DAILY, DAY, "s1");
        assertEquals(d0, sameDay);
        assertNotEquals(d0, nextDay);
    }

    @Test
    void weeklyKeyStableWithinWeek() {
        String w0 = MissionPeriod.key(MissionScope.WEEKLY, 0, "s1");
        String day6 = MissionPeriod.key(MissionScope.WEEKLY, 6 * DAY, "s1");
        String day7 = MissionPeriod.key(MissionScope.WEEKLY, 7 * DAY, "s1");
        assertEquals(w0, day6);
        assertNotEquals(w0, day7);
    }

    @Test
    void seasonalKeyFollowsSeasonId() {
        assertEquals("Ss1", MissionPeriod.key(MissionScope.SEASONAL, 0, "s1"));
        assertEquals("Ss2", MissionPeriod.key(MissionScope.SEASONAL, 999 * DAY, "s2"));
    }

    @Test
    void progressCapsAtTarget() {
        MissionProgress mp = new MissionProgress();
        assertEquals(5, mp.add("m", 5, 10));
        assertEquals(10, mp.add("m", 999, 10)); // plafonné à la cible
        assertEquals(10, mp.count("m"));
    }
}
