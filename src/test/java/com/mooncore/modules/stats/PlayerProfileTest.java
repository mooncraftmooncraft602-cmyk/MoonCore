package com.mooncore.modules.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileTest {

    private PlayerProfile profile() {
        return new PlayerProfile(UUID.randomUUID(), "Steve", 0, 0, 0, "season-1");
    }

    @Test
    void incrementAccumulatesAndMarksDirty() {
        PlayerProfile p = profile();
        assertFalse(p.isDirty());
        assertEquals(0, p.get("kills"));

        assertEquals(3, p.add("kills", 3));
        assertEquals(5, p.add("kills", 2));
        assertTrue(p.isDirty());
        assertEquals(5, p.get("kills"));
    }

    @Test
    void clearDirtyResetsFlag() {
        PlayerProfile p = profile();
        p.add("blocks", 1);
        assertTrue(p.isDirty());
        p.clearDirty();
        assertFalse(p.isDirty());
        // Une nouvelle écriture re-marque dirty.
        p.set("blocks", 10);
        assertTrue(p.isDirty());
        assertEquals(10, p.get("blocks"));
    }

    @Test
    void playtimeAccumulates() {
        PlayerProfile p = profile();
        p.addPlaytimeSeconds(60);
        p.addPlaytimeSeconds(60);
        assertEquals(120, p.playtimeSeconds());
        assertTrue(p.isDirty());
    }
}
