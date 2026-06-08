package com.mooncore.modules.antifarm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiFarmLogicTest {

    private SpawnerRegistry.Entry entry(int x, int y, int z, UUID owner, String team) {
        return new SpawnerRegistry.Entry("world", x, y, z, owner, team);
    }

    @Test
    void registryCountsByChunkOwnerTeam() {
        SpawnerRegistry reg = new SpawnerRegistry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(reg.add(entry(0, 64, 0, a, "red")));
        assertTrue(reg.add(entry(1, 64, 1, a, "red")));   // même chunk (0,0)
        assertTrue(reg.add(entry(20, 64, 20, b, "red")));  // chunk (1,1)

        assertEquals(2, reg.chunkCount("world", 0, 0));
        assertEquals(1, reg.chunkCount("world", 1, 1));
        assertEquals(2, reg.ownerCount(a));
        assertEquals(1, reg.ownerCount(b));
        assertEquals(3, reg.teamCount("red"));
        assertEquals(3, reg.total());
    }

    @Test
    void registryRejectsDuplicateAndDecrementsOnRemove() {
        SpawnerRegistry reg = new SpawnerRegistry();
        UUID a = UUID.randomUUID();
        assertTrue(reg.add(entry(0, 64, 0, a, null)));
        assertFalse(reg.add(entry(0, 64, 0, a, null))); // doublon à la même position

        assertEquals(1, reg.ownerCount(a));
        SpawnerRegistry.Entry removed = reg.remove("world", 0, 64, 0);
        assertEquals(a, removed.owner());
        assertEquals(0, reg.ownerCount(a));
        assertEquals(0, reg.chunkCount("world", 0, 0));
    }

    @Test
    void yieldFullBelowSoftCap() {
        YieldLimiter yl = new YieldLimiter(3, 9, 60_000, 0.2);
        UUID p = UUID.randomUUID();
        double f = 1.0;
        for (int i = 0; i < 3; i++) f = yl.recordAndFactor(p, 1000);
        assertEquals(1.0, f, 1e-9);
    }

    @Test
    void yieldMinAtHardCap() {
        YieldLimiter yl = new YieldLimiter(3, 9, 60_000, 0.2);
        UUID p = UUID.randomUUID();
        double f = 1.0;
        for (int i = 0; i < 9; i++) f = yl.recordAndFactor(p, 1000);
        assertEquals(0.2, f, 1e-9);
    }

    @Test
    void yieldInterpolatesBetweenCaps() {
        YieldLimiter yl = new YieldLimiter(0, 10, 60_000, 0.0);
        UUID p = UUID.randomUUID();
        double f = 0;
        for (int i = 0; i < 5; i++) f = yl.recordAndFactor(p, 1000);
        // 5 kills sur [0..10] avec minFactor 0 → facteur 0.5
        assertEquals(0.5, f, 1e-9);
    }

    @Test
    void yieldWindowExpires() {
        YieldLimiter yl = new YieldLimiter(2, 5, 1000, 0.1);
        UUID p = UUID.randomUUID();
        yl.recordAndFactor(p, 0);
        yl.recordAndFactor(p, 0);
        yl.recordAndFactor(p, 0); // 3 kills à t=0
        // Bien après la fenêtre : les anciens kills expirent.
        assertEquals(0, yl.recentKills(p, 5000));
        assertEquals(1.0, yl.factor(p, 5000), 1e-9);
    }
}
