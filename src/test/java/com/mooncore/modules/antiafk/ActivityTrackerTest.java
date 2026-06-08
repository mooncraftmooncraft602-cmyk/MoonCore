package com.mooncore.modules.antiafk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTrackerTest {

    private static final long THRESHOLD = 300_000; // 5 min

    @Test
    void idleGrowsUntilThreshold() {
        ActivityTracker t = new ActivityTracker();
        UUID p = UUID.randomUUID();
        t.record(p, 0);

        // Pas encore AFK avant le seuil.
        assertFalse(t.evaluate(p, THRESHOLD - 1, THRESHOLD));
        assertFalse(t.isAfk(p));

        // Devient AFK au seuil : transition signalée.
        assertTrue(t.evaluate(p, THRESHOLD, THRESHOLD));
        assertTrue(t.isAfk(p));

        // Réévaluation sans changement : aucune transition.
        assertFalse(t.evaluate(p, THRESHOLD + 1000, THRESHOLD));
        assertTrue(t.isAfk(p));
    }

    @Test
    void activityResetsAfk() {
        ActivityTracker t = new ActivityTracker();
        UUID p = UUID.randomUUID();
        t.record(p, 0);
        t.evaluate(p, THRESHOLD, THRESHOLD);
        assertTrue(t.isAfk(p));

        // Nouvelle activité : transition retour actif.
        t.record(p, THRESHOLD + 5000);
        assertTrue(t.evaluate(p, THRESHOLD + 5000, THRESHOLD));
        assertFalse(t.isAfk(p));
    }
}
